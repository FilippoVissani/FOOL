package compiler;

import compiler.AST.*;
import compiler.lib.*;

public class TypeRels {

	// valuta se il tipo "a" e' <= al tipo "b", dove "a" e "b" sono tipi di base: IntTypeNode o BoolTypeNode
	public static boolean isSubtype(TypeNode a, TypeNode b) {
		if ((a instanceof IntTypeNode && b instanceof IntTypeNode)
				|| (a instanceof BoolTypeNode && b instanceof BoolTypeNode)
				|| (a instanceof BoolTypeNode && b instanceof IntTypeNode)) {
			return true;
		}

		// OBJECT-ORIENTED EXTENSION

		if (a instanceof EmptyTypeNode && b instanceof RefTypeNode){
			return true;
		}
		if (a instanceof RefTypeNode && b instanceof RefTypeNode
				&& ((RefTypeNode) a).id.equals(((RefTypeNode) b).id)){
			return true;
		}
		return false;
	}
}
