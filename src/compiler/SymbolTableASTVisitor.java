package compiler;

import java.util.*;
import compiler.AST.*;
import compiler.exc.*;
import compiler.lib.*;

public class SymbolTableASTVisitor extends BaseASTVisitor<Void,VoidException> {
	
	private List<Map<String, STentry>> symTable = new ArrayList<>();
	// utilizzata per mantenere le dichiarazioni interne alle classi
	private Map<String, Map<String,STentry>> classTable = new HashMap<>();
	private int nestingLevel=0; // current nesting level
	private int decOffset=-2; // counter for offset of local declarations at current nesting level 
	int stErrors=0;

	SymbolTableASTVisitor() {}
	SymbolTableASTVisitor(boolean debug) {super(debug);} // enables print for debugging

	private STentry stLookup(String id) {
		int j = nestingLevel;
		STentry entry = null;
		while (j >= 0 && entry == null) 
			entry = symTable.get(j--).get(id);	
		return entry;
	}

	@Override
	public Void visitNode(ProgLetInNode n) {
		if (print) printNode(n);
		Map<String, STentry> hm = new HashMap<>();
		symTable.add(hm);
	    for (Node dec : n.declist) visit(dec);
		visit(n.exp);
		symTable.remove(0);
		return null;
	}

	@Override
	public Void visitNode(ProgNode n) {
		if (print) printNode(n);
		visit(n.exp);
		return null;
	}
	
	@Override
	public Void visitNode(FunNode n) {
		if (print) printNode(n);
		Map<String, STentry> hm = symTable.get(nestingLevel);
		List<TypeNode> parTypes = new ArrayList<>();  
		for (ParNode par : n.parlist) parTypes.add(par.getType()); 
		STentry entry = new STentry(nestingLevel, new ArrowTypeNode(parTypes,n.retType),decOffset--);
		//inserimento di ID nella symtable
		if (hm.put(n.id, entry) != null) {
			System.out.println("Fun id " + n.id + " at line "+ n.getLine() +" already declared");
			stErrors++;
		} 
		//creare una nuova hashmap per la symTable
		nestingLevel++;
		Map<String, STentry> hmn = new HashMap<>();
		symTable.add(hmn);
		int prevNLDecOffset=decOffset; // stores counter for offset of declarations at previous nesting level 
		decOffset=-2;
		
		int parOffset=1;
		for (ParNode par : n.parlist)
			if (hmn.put(par.id, new STentry(nestingLevel,par.getType(),parOffset++)) != null) {
				System.out.println("Par id " + par.id + " at line "+ n.getLine() +" already declared");
				stErrors++;
			}
		for (Node dec : n.declist) visit(dec);
		visit(n.exp);
		//rimuovere la hashmap corrente poiche' esco dallo scope
		symTable.remove(nestingLevel--);
		decOffset=prevNLDecOffset; // restores counter for offset of declarations at previous nesting level 
		return null;
	}
	
	@Override
	public Void visitNode(VarNode n) {
		if (print) printNode(n);
		visit(n.exp);
		Map<String, STentry> hm = symTable.get(nestingLevel);
		STentry entry = new STentry(nestingLevel,n.getType(),decOffset--);
		//inserimento di ID nella symtable
		if (hm.put(n.id, entry) != null) {
			System.out.println("Var id " + n.id + " at line "+ n.getLine() +" already declared");
			stErrors++;
		}
		return null;
	}

	@Override
	public Void visitNode(PrintNode n) {
		if (print) printNode(n);
		visit(n.exp);
		return null;
	}

	@Override
	public Void visitNode(IfNode n) {
		if (print) printNode(n);
		visit(n.cond);
		visit(n.th);
		visit(n.el);
		return null;
	}
	
	@Override
	public Void visitNode(EqualNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}
	
	@Override
	public Void visitNode(TimesNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}
	
	@Override
	public Void visitNode(PlusNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}

	@Override
	public Void visitNode(CallNode n) {
		if (print) printNode(n);
		STentry entry = stLookup(n.id);
		if (entry == null) {
			System.out.println("Fun id " + n.id + " at line "+ n.getLine() + " not declared");
			stErrors++;
		} else {
			n.entry = entry;
			n.nl = nestingLevel;
		}
		for (Node arg : n.arglist) visit(arg);
		return null;
	}

	@Override
	public Void visitNode(IdNode n) {
		if (print) printNode(n);
		STentry entry = stLookup(n.id);
		if (entry == null) {
			System.out.println("Var or Par id " + n.id + " at line "+ n.getLine() + " not declared");
			stErrors++;
		} else {
			n.entry = entry;
			n.nl = nestingLevel;
		}
		return null;
	}

	@Override
	public Void visitNode(BoolNode n) {
		if (print) printNode(n, n.val.toString());
		return null;
	}

	@Override
	public Void visitNode(IntNode n) {
		if (print) printNode(n, n.val.toString());
		return null;
	}

	// OPERATOR EXTENSION

	@Override
	public Void visitNode(GreaterEqualNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}

	@Override
	public Void visitNode(LessEqualNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}

	@Override
	public Void visitNode(NotNode n) {
		if (print) printNode(n);
		visit(n.exp);
		return null;
	}

	@Override
	public Void visitNode(MinusNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}

	@Override
	public Void visitNode(OrNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}

	@Override
	public Void visitNode(DivNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}

	@Override
	public Void visitNode(AndNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}

	// OBJECT-ORIENTED EXTENSION

	@Override
	public Void visitNode(ClassNode n) {
		if (print) printNode(n);
		Map<String, STentry> hm = symTable.get(nestingLevel);
		// ArrayList per campi e per metodi
		ClassTypeNode classTypeNode = new ClassTypeNode(new ArrayList<>(), new ArrayList<>());
		// Creo una entry (relativa alla classe) nel nesting level 0
		STentry entry = new STentry(0, classTypeNode,decOffset--);
		//inserimento di ID nella symtable
		if (hm.put(n.id, entry) != null) {
			System.out.println("Class id " + n.id + " at line "+ n.getLine() +" already declared");
			stErrors++;
		}
		// creazione della virtual table
		classTable.put(n.id, new HashMap<>());
		// creo un nuovo livello nella symbol table, che punta alla stessa virtual table appena creata
		nestingLevel++;
		symTable.add(nestingLevel, classTable.get(n.id));
		// Aggiornamento campi virtual table (da -1 a -n)
		int fieldOffset=-1;
		for (FieldNode field : n.fields){
			if (classTable.get(n.id).put(field.id, new STentry(nestingLevel, field.getType(), fieldOffset)) != null){
				System.out.println("Field id " + field.id + " at line "+ n.getLine() +" already declared");
				stErrors++;
			}
			// aggiungo il campo in allFields in posizione -offset-1 (da 0 a n-1)
			classTypeNode.allFields.add(-fieldOffset-1, field.getType());
			fieldOffset--;
		}
		// Aggiornamento metodi virtual table (da 0 a m-1)
		int prevNLDecOffset=decOffset; // stores counter for offset of declarations at previous nesting level
		decOffset=0;
		for (MethodNode method : n.methods) {
			if (classTable.get(n.id).put(method.id, new STentry(nestingLevel, method.getType(), decOffset)) != null){
				System.out.println("Method id " + method.id + " at line "+ n.getLine() +" already declared");
				stErrors++;
			}
			visit(method);
			// aggiungo il metodo in allMethods in posizione offset (da 0 a m-1)
			classTypeNode.allMethods.add(decOffset,(ArrowTypeNode) method.getType());
			decOffset++;
		}
		//rimuovere la hashmap corrente poiche' esco dallo scope
		symTable.remove(nestingLevel--);
		decOffset=prevNLDecOffset; // restores counter for offset of declarations at previous nesting level
		return null;
	}

	@Override
	public Void visitNode(MethodNode n) {
		if (print) printNode(n);
		n.offset = decOffset;
		// TODO controllare
		List<TypeNode> parametersTypes = new ArrayList<>();
		n.parlist.forEach(parameter -> parametersTypes.add(parameter.getType()));
		STentry entry = new STentry(nestingLevel, new MethodTypeNode(parametersTypes,n.getType()),decOffset--);
		//inserimento di ID nella symtable
		if (symTable.get(nestingLevel).put(n.id, entry) != null) {
			System.out.println("Method id " + n.id + " at line "+ n.getLine() +" already declared");
			stErrors++;
		}
		//crea una nuova hashmap per la symTable
		nestingLevel++;
		Map<String, STentry> hmn = new HashMap<>();
		symTable.add(hmn);
		int prevNLDecOffset=decOffset; // stores counter for offset of declarations at previous nesting level
		decOffset=-2;

		for (ParNode parameter : n.parlist) {
			if (hmn.put(parameter.id, new STentry(nestingLevel,parameter.getType(), n.parlist.indexOf(parameter)+1)) != null) {
				System.out.println("Par id " + parameter.id + " at line "+ n.getLine() +" already declared");
				stErrors++;
			}
		}
		n.declist.forEach(this::visit);
		visit(n.exp);
		//rimuovere la hashmap corrente poiche' esco dallo scope
		symTable.remove(nestingLevel--);
		decOffset=prevNLDecOffset; // restores counter for offset of declarations at previous nesting level
		return null;
	}

	@Override
	public Void visitNode(ClassCallNode n) {
		// TODO: controllare
		if (print) printNode(n);
		STentry entry = stLookup(n.id1);
		if (entry == null) {
			System.out.println("Object id " + n.id1 + " at line "+ n.getLine() + " not declared");
			stErrors++;
		} else {
			n.entry = entry;
			n.nl = nestingLevel;
		}
		// cercata nella Virtual Table (raggiunta tramite la Class Table) della classe del tipo RefTypeNode di ID1
		Map<String,STentry> virtualTable = classTable.get(((RefTypeNode) entry.type).id);
		if (virtualTable == null || virtualTable.get(n.id2) == null) {
			System.out.println("Method id " + n.id2 + " at line "+ n.getLine() + " not declared");
			stErrors++;
		} else {
			n.methodEntry = virtualTable.get(n.id2);
		}
		for (Node arg : n.arglist) visit(arg);
		return null;
	}

	@Override
	public Void visitNode(NewNode n) throws VoidException {
		if (print) printNode(n);
		/*
		* STentry della classe ID in campo "entry"
		* ID deve essere in Class Table e STentry presa
		* direttamente da livello 0 della Symbol Table
		* */
		int oldNL = nestingLevel;
		nestingLevel = 0;
		STentry entry = stLookup(n.id);
		nestingLevel = oldNL;
		if (entry == null || !classTable.containsKey(n.id)) {
			System.out.println("Class id " + n.id + " at line "+ n.getLine() + " not declared");
			stErrors++;
		} else {
			n.entry = entry;
			n.nl = nestingLevel;
		}
		return null;
	}
}
