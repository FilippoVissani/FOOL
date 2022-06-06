package compiler;

import compiler.AST.*;
import compiler.lib.*;
import compiler.exc.*;
import svm.ExecuteVM;

import java.util.ArrayList;
import java.util.List;

import static compiler.lib.FOOLlib.*;

public class CodeGenerationASTVisitor extends BaseASTVisitor<String, VoidException> {

  CodeGenerationASTVisitor() {}
  CodeGenerationASTVisitor(boolean debug) {super(false,debug);} //enables print for debugging

	/*
	LAYOUT AR DELL'AMBIENTE GLOBALE

	[BASE DELLO STACK E' QUI SOTTO]           <- $fp in codice "main"
	Return Address fittizio 0 (si va in halt)
	valore/addr prima var/funz dichiarata     [offset -2]
	valore/addr seconda var/funz              [offset -3]
	.
	.
	valore/addr ultima (n-esima) var/funz     [offset -(n+1)]
	* */
	@Override
	public String visitNode(ProgLetInNode n) {
		if (print) printNode(n);
		String declCode = null;
		for (Node dec : n.declist) declCode=nlJoin(declCode,visit(dec));
		return nlJoin(
			"push 0",	// ra fittizio per uniformare gli offset
			declCode, // generate code for declarations (allocation)			
			visit(n.exp),
			"halt",
			getCode()
		);
	}

	@Override
	public String visitNode(ProgNode n) {
		if (print) printNode(n);
		return nlJoin(
			visit(n.exp),
			"halt"
		);
	}

	@Override
	public String visitNode(FunNode n) {
	  /*
	  salva il corpo della funzione sfruttando FOOLLib.putCode().
	  Il corpo verrà recuperato successivamente grazie a FOOLLib.getCode() e posto subito dopo halt
	  * */
		if (print) printNode(n,n.id);
		String declCode = null, popDecl = null, popParl = null;
		for (Node dec : n.declist) {
			declCode = nlJoin(declCode,visit(dec));
			popDecl = nlJoin(popDecl,"pop");
		}
		for (int i=0;i<n.parlist.size();i++) popParl = nlJoin(popParl,"pop");
		String funl = freshFunLabel();
		putCode(
			nlJoin(
				funl+":", // label della funzione
				"cfp", // set $fp to $sp value, $fp punta al nuovo record di attivazione
				"lra", // push di $ra sullo stack, utilizzato per accedere all'istruzione successiva alla terminazione della chiamata
				declCode, // generate code for local declarations (they use the new $fp!!!)
				visit(n.exp), // generate code for function body expression
				"stm", // set $tm to popped value (function result)
				popDecl, // remove local declarations from stack
				"sra", // set $ra to popped value
				"pop", // remove Access Link from stack
				popParl, // remove parameters from stack
				"sfp", // set $fp to popped value (Control Link), il controllo ritorna alla funzione chiamante
				"ltm", // load $tm value (function result)
				"lra", // load $ra value, carico l'indirizzo che contiene l'istruzione da eseguire alla fine della chiamata
				"js"  // jump to popped address (saving address of subsequent instruction in $ra)
			)
		);
		return "push "+funl;		
	}

	@Override
	public String visitNode(VarNode n) {
		if (print) printNode(n,n.id);
		return visit(n.exp);
	}

	@Override
	public String visitNode(PrintNode n) {
		if (print) printNode(n);
		return nlJoin(
			visit(n.exp),
			"print"
		);
	}

	@Override
	public String visitNode(IfNode n) {
		if (print) printNode(n);
		String l1 = freshLabel();
		String l2 = freshLabel();
		return nlJoin(
		visit(n.cond),
		"push 1",
		"beq "+l1,
		visit(n.el),
		"b "+l2,
		l1+":",
		visit(n.th),
		l2+":"
		);
	}

	@Override
	public String visitNode(EqualNode n) {
		if (print) printNode(n);
		String l1 = freshLabel();
	 	String l2 = freshLabel();
		return nlJoin(
			visit(n.left),
			visit(n.right),
			"beq "+l1,
			"push 0",
			"b "+l2,
			l1+":",
			"push 1",
			l2+":"
		);
	}

	@Override
	public String visitNode(TimesNode n) {
		if (print) printNode(n);
		return nlJoin(
			visit(n.left),
			visit(n.right),
			"mult"
		);	
	}

	@Override
	public String visitNode(PlusNode n) {
		if (print) printNode(n);
		return nlJoin(
			visit(n.left),
			visit(n.right),
			"add"
		);
	}

	/*
	* 	LAYOUT AR DI UNA FUNZIONE (STACK CRESCE VERSO IL BASSO!)

		CL:address (fp) di AR chiamante
		valore ultimo (m-esimo) parametro         [offset m]
		.
		.
		valore primo parametro                    [offset 1]
		AL:address (fp) di AR dichiarazione       <- $fp in codice body della funz
		Return Address
		valore/addr prima var/funz dichiarata     [offset -2]
		valore/addr seconda var/funz              [offset -3]
		.
		.
		valore/addr ultima (n-esima) var/funz     [offset -(n+1)]
	* */
	@Override
	public String visitNode(CallNode n) {
		if (print) printNode(n,n.id);
		String argCode = null;
		String getAR = null;
		// codice degli argomenti al contrario (m -> 1)
		for (int i = n.arglist.size() - 1; i >= 0 ; i--) argCode = nlJoin(argCode,visit(n.arglist.get(i)));
		// codice per accedere alla dichiarazione della funzione/metodo
		// risale la catena statica degli AL
		for (int i = 0; i < n.nl - n.entry.nl; i++) getAR = nlJoin(getAR,"lw");
		if ((n.entry.type instanceof MethodTypeNode)){
			return nlJoin(
					"lfp", // prendo $fp (che punta al chiamante) e lo pusho sullo stack, verrà usato come Control Link
					argCode, // codice degli argomenti in ordine inverso
					"lfp", // metto sullo stack il frame pointer per risalire la catena di AL
					getAR, // retrieve address of frame containing "id" declaration
							// by following the static chain (of Access Links)
							// sulla cima dello stack ho l'AL, che deve puntare al frame con la dichiarazione della funzione.
							// siccome oltre a lasciare l'AL sulla cima dello stack (per rispettare il layout) lo devo anche usare
							// allora lo duplico usando il registro temporaneo
					"stm", // set $tm to popped value (with the aim of duplicating top of stack)
					"ltm", // load Access Link (pointer to frame of function "id" declaration)
					"ltm", // duplicate top of stack
					"lw", // dereferenzio e accedo alla dispatch table
					"push " + n.entry.offset,
					"add", // calcolo l'indirizzo della dichiarazione del metodo
					"lw", // carico l'indirizzo della dichiarazione a cui saltare
					"js"  // jump to popped address (saving address of subsequent instruction in $ra)
			);
		} else {
			return nlJoin(
					"lfp", // prendo $fp (che punta ancora al chiamante) e lo pusho sullo stack, verrà usato come Control Link
					argCode, // codice degli argomenti in ordine inverso
					"lfp", // metto sullo stack il frame pointer per risalire la catena di AL
					getAR, // retrieve address of frame containing "id" declaration
							// by following the static chain (of Access Links)
							// sulla cima dello stack ho l'AL, che deve puntare al frame con la dichiarazione della funzione.
							// siccome oltre a lasciare l'AL sulla cima dello stack (per rispettare il layout) lo devo anche usare
							// allora lo duplico usando il registro temporaneo
					"stm", // set $tm to popped value (with the aim of duplicating top of stack)
					"ltm", // load Access Link (pointer to frame of function "id" declaration)
					"ltm", // duplicate top of stack
					"push "+ n.entry.offset,
					"add", // calcolo l'indirizzo della dichiarazione della funzione
					"lw", // carico l'indirizzo della dichiarazione a cui saltare
					"js"  // jump to popped address (saving address of subsequent instruction in $ra)
			);
		}
	}

	@Override
	public String visitNode(IdNode n) {
		if (print) printNode(n,n.id);
		String getAR = null;
		// codice per accedere alla dichiarazione della variabile
		// risale la catena statica degli AL
		for (int i = 0;i<n.nl-n.entry.nl;i++) getAR=nlJoin(getAR,"lw");
		return nlJoin(
				"lfp",
				getAR, // retrieve address of frame containing "id" declaration
						// by following the static chain (of Access Links)
				"push "+n.entry.offset, "add", // compute address of "id" declaration
				"lw" // load value of "id" variable
		);
	}

	@Override
	public String visitNode(BoolNode n) {
		if (print) printNode(n,n.val.toString());
		return "push "+(n.val?1:0);
	}

	@Override
	public String visitNode(IntNode n) {
		if (print) printNode(n,n.val.toString());
		return "push "+n.val;
	}

	// OPERATOR EXTENSION

	@Override
	public String visitNode(GreaterEqualNode n) {
		if (print) printNode(n);
		String l1 = freshLabel();
		String l2 = freshLabel();
		return nlJoin(
				visit(n.right),
				visit(n.left),
				"sub", // right - left
				"push 0",
				"bleq "+l1,
				"push 0",
				"b "+l2,
				l1+":",
				"push 1",
				l2+":"
		);
	}

	@Override
	public String visitNode(LessEqualNode n) {
		if (print) printNode(n);
		String l1 = freshLabel();
		String l2 = freshLabel();
		return nlJoin(
				visit(n.left),
				visit(n.right),
				"bleq "+l1,
				"push 0",
				"b "+l2,
				l1+":",
				"push 1",
				l2+":"
		);
	}

	@Override
	public String visitNode(NotNode n) {
		if (print) printNode(n);
		String l1 = freshLabel();
		String l2 = freshLabel();
		return nlJoin(
				visit(n.exp),
				"push 0",
				"beq "+l1,
				"push 0",
				"b "+l2,
				l1+":",
				"push 1",
				l2+":"
		);
	}

	@Override
	public String visitNode(MinusNode n) {
		if (print) printNode(n);
		return nlJoin(
				visit(n.left),
				visit(n.right),
				"sub"
		);
	}

	@Override
	public String visitNode(OrNode n) {
		if (print) printNode(n);
		String l1 = freshLabel();
		String l2 = freshLabel();
		return nlJoin(
				visit(n.left),
				"push 1",
				"beq "+l1,
				visit(n.right),
				"push 1",
				"beq "+l1,
				"push 0",
				"b "+l2,
				l1+":",
				"push 1",
				l2+":"
		);
	}

	@Override
	public String visitNode(DivNode n) {
		if (print) printNode(n);
		return nlJoin(
				visit(n.left),
				visit(n.right),
				"div"
		);
	}

	@Override
	public String visitNode(AndNode n) {
		if (print) printNode(n);
		String l1 = freshLabel();
		String l2 = freshLabel();
		return nlJoin(
				visit(n.left),
				"push 0",
				"beq "+l1,
				visit(n.right),
				"push 0",
				"beq "+l1,
				"push 1",
				"b "+l2,
				l1+":",
				"push 0",
				l2+":"
		);
	}

	// OBJECT-ORIENTED EXTENSION
	/*
	[PRIMA POSIZIONE LIBERA HEAP] 		<- $hp subito dopo allocazione oggetto
	dispatch pointer					[offset 0] <- object pointer
	valore primo campo dichiarato		[offset -1]
	.
	.
	valore ultimo (n-esimo) campo		[offset -n]
	*/

	@Override
	public String visitNode(ClassNode n) {
		if (print) printNode(n, n.id);
		/*
		 * Ritorna codice che alloca su heap la dispatch table
		 * della classe e lascia il dispatch pointer sullo stack
		 */
		// costruzione Dispatch Table
		// 1 - creo la Dispatch Table
		List<String> dispatchTable = new ArrayList<>();
		/*
		2 - considero in ordine di apparizione i miei figli metodi (in campo methods),
		per ciascuno di essi:
			- invoco la sua visit()
			- leggo l’etichetta a cui è stato posto il suo codice dal suo campo
				"label" e il suo offset dal suo campo "offset"
			- aggiorno la Dispatch Table creata settando la posizione data
				dall’offset del metodo alla sua etichetta
		 */
		n.methods.forEach(method -> {
			visit(method);
			dispatchTable.add(method.label);
		});
		// codice ritornato
		/*
		1 - metto valore di $hp sullo stack: sarà il dispatch
		pointer da ritornare alla fine.
		2 - creo sullo heap la Dispatch Table che ho costruito: la
		scorro dall’inizio alla fine e, per ciascuna etichetta,
		la memorizzo a indirizzo in $hp e incremento $hp
		 */
		String buildDispatchTable = nlJoin("lhp"); // push di hp sullo stack, ingresso per la dispatch table
		for (String methodLabel: dispatchTable) {
			buildDispatchTable = nlJoin(buildDispatchTable,
					"push " + methodLabel, // push della label del metodo sullo stack
					"lhp", // push di hp sullo stack
					"sw", // pop dei due valori e metto il secondo all'indirizzo puntato dal primo (label del metodo nello heap)
					"lhp", // push di hp sullo stack
					"push 1", // push di 1 sullo stack
					"add", // sommo i due valori
					"shp"); // memorizzo il risultato in hp (incremento hp di 1)
		}
		return buildDispatchTable;
	}

	/*
	[PRIMA POSIZIONE LIBERA HEAP] 		<- $hp subito dopo allocazione tabella
	addr ultimo (m-esimo) metodo		[offset m-1]
	.
	.
	addr primo metodo dichiarato		[offset 0] <- dispatch pointer
	* */
	@Override
	public String visitNode(MethodNode n) {
		if (print) printNode(n,n.id);
		/*
		* genera un’etichetta nuova per il suo indirizzo e la
		* mette nel suo campo "label"
		*/
		String methl = freshLabel();
		n.label = methl;
		/*
		* genera il codice del metodo (invariato rispetto a
		* funzioni) e lo inserisce in FOOLlib con putCode()
		*/
		String declCode = null;
		String popDecl = null;
		String popParl = null;
		for (Node dec : n.declist) {
			declCode = nlJoin(declCode,visit(dec));
			popDecl = nlJoin(popDecl,"pop");
		}
		for (ParNode p : n.parlist) popParl = nlJoin(popParl,"pop");
		putCode(
				nlJoin(
						methl+":", // label del metodo
						"cfp", // set $fp to $sp value, l'Acces Link è sulla cima dello stack
						"lra", // push di $ra sullo stack, utilizzato per accedere all'istruzione successiva alla terminazione della chiamata
						declCode, // generate code for local declarations (they use the new $fp!!!)
						visit(n.exp), // generate code for method body expression
						"stm", // set $tm to popped value (method result)
						popDecl, // remove local declarations from stack
						"sra", // set $ra to popped value
						"pop", // remove Access Link from stack
						popParl, // remove parameters from stack
						"sfp", // set $fp to popped value (Control Link), il controllo ritorna alla funzione chiamante
						"ltm", // load $tm value (function result)
						"lra", // // load $ra value, carico l'indirizzo che contiene l'istruzione da eseguire alla fine della chiamata
						"js"  // jump to popped address (saving address of subsequent instruction in $ra)
				)
		);
		//ritorna codice vuoto (null)
		return null;
	}

	@Override
	public String visitNode(ClassCallNode n) throws VoidException {
		if (print) printNode(n,n.id1 + "." + n.id2);
		/*
		* inizia la costruzione dell’AR del metodo ID2 invocato:
		* 	dopo aver messo sullo stack il Control Link e il valore dei parametri,
		* 	fin qui il codice generato è invariato rispetto a CallNode
		*
		* 	recupera valore dell'ID1 (object pointer) dall'AR dove è
		* 	dichiarato con meccanismo usuale di risalita catena statica
		* 	(come per IdNode) e lo usa:
		* 		per settare a tale valore l’Access Link mettendolo sullo
		* 		stack e, duplicandolo,
		* 		per recuperare (usando l’offset di ID2 nella dispatch
		* 		table riferita dal dispatch pointer dell’oggetto)
		* 		l'indirizzo del metodo a cui saltare
		* */
		String argCode = null;
		String getAR = null;
		// argCode generato visitando le espressioni degli argomenti al contrario
		for (int i = n.arglist.size() - 1; i >= 0; i--) argCode=nlJoin(argCode,visit(n.arglist.get(i)));
		// nesting level della chamata - nesting level della dichiarazione dell'oggetto (ID1)
		// risale la catena di AR a partire da quello corrente
		for (int i = 0; i < n.nl - n.entry.nl; i++) getAR=nlJoin(getAR,"lw");
		return nlJoin(
				"lfp", // load Control Link (pointer to frame of function "id" caller)
				argCode, // generate code for argument expressions in reversed order
				"lfp", // metto sullo stack il frame pointer per risalire la catena di AL
				getAR, // retrieve address of frame containing "id" declaration
				// by following the static chain (of Access Links)
				"push "+n.entry.offset,
				"add", // compute address of "id" declaration
				"lw", // push del dispatch pointer sullo sack
				"stm", // set $tm to popped value (with the aim of duplicating top of stack)
				"ltm",
				"ltm", // duplico il dispatch pointer
				"lw", // carico il primo indirizzo della dispatch table sullo stack
				"push "+n.methodEntry.offset,
				"add", // calcolo l'indirizzo del metodo a cui saltare
				"lw", // salto all'indirizzo del metodo
				"js"  // jump to popped address (saving address of subsequent instruction in $ra)
		);
	}

	@Override
	public String visitNode(NewNode n) throws VoidException {
		if (print) printNode(n, n.id);
		String argCode = null;
		String putArgToHeapCode = null;
		for (Node arg : n.arglist) {
			/*
			* – prima:
			* 	• si richiama su tutti gli argomenti in ordine di apparizione
			* 	(che mettono ciascuno il loro valore calcolato sullo stack)
			*/
			argCode=nlJoin(argCode,visit(arg));
			/*
			* – poi:
			* 	• prende i valori degli argomenti, uno alla volta, dallo stack e li
			* 	mette nello heap, incrementando $hp dopo ogni singola copia
			*/
			putArgToHeapCode=nlJoin(
					putArgToHeapCode,
					"lhp", // metto sullo stack hp
					"sw", // metto il valore dell'argomento sullo heap
					"lhp", // metto sullo stack hp
					"push 1",
					"add",
					"shp" //incremento $hp
			);
		}
		return nlJoin(
				argCode,
				putArgToHeapCode,
				"push " + (ExecuteVM.MEMSIZE + n.entry.offset),
				"lw",
				"lhp",
				"sw", //scrive a indirizzo $hp il dispatch pointer recuperandolo
					  // contenuto indirizzo MEMSIZE + offset classe ID
				"lhp", // carico sullo stack l'object pointer
				"lhp", // lo duplico
				"push 1",
				"add",
				"shp" // incremento $hp per farlo puntare alla cima dello heap
		);
	}

	@Override
	public String visitNode(EmptyNode n) throws VoidException {
		if (print) printNode(n);
		/*
		mette sullo stack il valore -1
		sicuramente diverso da object pointer di ogni oggetto creato
		 */
		return nlJoin(
				"push -1"
		);
	}
}
