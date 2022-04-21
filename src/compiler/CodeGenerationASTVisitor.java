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

	@Override
	public String visitNode(ProgLetInNode n) {
		if (print) printNode(n);
		String declCode = null;
		for (Node dec : n.declist) declCode=nlJoin(declCode,visit(dec));
		return nlJoin(
			"push 0",	
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
				funl+":",
				"cfp", // set $fp to $sp value
				"lra", // load $ra value
				declCode, // generate code for local declarations (they use the new $fp!!!)
				visit(n.exp), // generate code for function body expression
				"stm", // set $tm to popped value (function result)
				popDecl, // remove local declarations from stack
				"sra", // set $ra to popped value
				"pop", // remove Access Link from stack
				popParl, // remove parameters from stack
				"sfp", // set $fp to popped value (Control Link)
				"ltm", // load $tm value (function result)
				"lra", // load $ra value
				"js"  // jump to to popped address
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

	@Override
	public String visitNode(CallNode n) {
		if (print) printNode(n,n.id);
		String argCode = null;
		String getAR = null;
		for (int i=n.arglist.size()-1;i>=0;i--) argCode=nlJoin(argCode,visit(n.arglist.get(i)));
		for (int i = 0;i<n.nl-n.entry.nl;i++) getAR=nlJoin(getAR,"lw");
		if ((n.entry.type instanceof MethodTypeNode)){
			return nlJoin(
					"lfp", // load Control Link (pointer to frame of function "id" caller)
					argCode, // generate code for argument expressions in reversed order
					"lfp", getAR, // retrieve address of frame containing "id" declaration
					// by following the static chain (of Access Links)
					"stm", // set $tm to popped value (with the aim of duplicating top of stack)
					"ltm", // load Access Link (pointer to frame of function "id" declaration)
					"ltm", // duplicate top of stack (object pointer)
					"lw",
					"push " + n.entry.offset,
					"add", // compute address of "id" declaration
					"lw", // load address of "id" function
					"js"  // jump to popped address (saving address of subsequent instruction in $ra)
			);
		} else {
			return nlJoin(
					"lfp", // load Control Link (pointer to frame of function "id" caller)
					argCode, // generate code for argument expressions in reversed order
					"lfp", getAR, // retrieve address of frame containing "id" declaration
					// by following the static chain (of Access Links)
					"stm", // set $tm to popped value (with the aim of duplicating top of stack)
					"ltm", // load Access Link (pointer to frame of function "id" declaration)
					"ltm", // duplicate top of stack
					"push "+ n.entry.offset,
					"add", // compute address of "id" declaration
					"lw", // load address of "id" function
					"js"  // jump to popped address (saving address of subsequent instruction in $ra)
			);
		}
	}

	@Override
	public String visitNode(IdNode n) {
		if (print) printNode(n,n.id);
		String getAR = null;
		for (int i = 0;i<n.nl-n.entry.nl;i++) getAR=nlJoin(getAR,"lw");
		return nlJoin(
			"lfp", getAR, // retrieve address of frame containing "id" declaration
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
		String l3 = freshLabel();
		return nlJoin(
				visit(n.left),
				visit(n.right),
				"bleq "+l1,
				l3+":",
				"push 1",
				"b "+l2,
				l1+":",
				visit(n.left),
				visit(n.right),
				"beq "+l3,
				"push 0",
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
			dispatchTable.add(method.offset, method.label);
		});
		// codice ritornato
		/*
		1 - metto valore di $hp sullo stack: sarà il dispatch
		pointer da ritornare alla fine.
		2 - creo sullo heap la Dispatch Table che ho costruito: la
		scorro dall’inizio alla fine e, per ciascuna etichetta,
		la memorizzo a indirizzo in $hp e incremento $hp
		 */
		String buildDispatchTable = nlJoin("lhp");
		for (String methodLabel: dispatchTable) {
			buildDispatchTable = nlJoin(buildDispatchTable,
					"push " + methodLabel,
					"lhp",
					"sw",
					"lhp",
					"push 1",
					"add",
					"shp");
		}
		return buildDispatchTable;
	}

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
						//
						/*
						 * activation record (AR) = frame.
						 * - Il frame pointer non punta alla fine del frame
						 * - Il chiamato effettua delle pop per:
						 *	> il valore di ritorno
						 * 	> l’indirizzo di ritorno
						 * 	> gli argomenti
						 * 	> il valore salvato nel frame pointer
						 */
						methl+":",
						"cfp", // set $fp to $sp value
						"lra", // load $ra value
						declCode, // generate code for local declarations (they use the new $fp!!!)
						visit(n.exp), // generate code for method body expression
						"stm", // set $tm to popped value (method result)
						popDecl, // remove local declarations from stack
						"sra", // set $ra to popped value
						"pop", // remove Access Link from stack
						popParl, // remove parameters from stack
						"sfp", // set $fp to popped value (Control Link)
						"ltm", // load $tm value (function result)
						"lra", // load $ra value
						"js"  // jump to popped address
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
				"lfp",
				getAR, // retrieve address of frame containing "id" declaration
				// by following the static chain (of Access Links)
				"push "+n.entry.offset,
				"add", // compute address of "id" declaration
				"lw", // load value of "id" variable
				"stm", // set $tm to popped value (with the aim of duplicating top of stack)
				"ltm", // load Access Link (pointer to frame of function "id" declaration)
				"ltm", // duplicate top of stack
				"lw",
				"push "+n.methodEntry.offset,
				"add", // compute address of "id" declaration
				"lw", // load address of "id" function
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
					"lhp",
					"sw",
					"lhp",
					"push 1",
					"add",
					"shp");
		}
		return nlJoin(
				argCode,
				putArgToHeapCode,
				/*
				* 	• scrive a indirizzo $hp il dispatch pointer recuperandolo da
				* 	contenuto indirizzo MEMSIZE + offset classe ID
				*/
				"push " + ExecuteVM.MEMSIZE,  //dispatch pointer
				"push " + n.entry.offset,
				"add",
				"lw",
				/*
				* 	• carica sullo stack il valore di $hp (indirizzo object pointer
				* 	da ritornare) e incrementa $hp
				* – nota: anche se la classe ID non ha campi l’oggetto
				* allocato contiene comunque il dispatch pointer
				* 	• == tra object pointer ottenuti da due new è sempre falso!
				* */
				"lhp",	// object pointer
				"sw",
				"lhp",
				"lhp",
				"push 1",
				"add",
				"shp"
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
