grammar SVM;

@parser::header {
import java.util.*;
}

@lexer::members {
public int lexicalErrors=0;
}
   
@parser::members { 
public int[] code = new int[ExecuteVM.CODESIZE];    
private int i = 0;
// ASSOCIA AD UNA LABEL IL SUO INDIRIZZO (DEFINIZIONE).
private Map<String,Integer> labelDef = new HashMap<>();
// ASSOCIA AD UN INDIRIZZO LA LABEL A CUI FA RIFERIMENTO (RIFERIMENTO).
private Map<Integer,String> labelRef = new HashMap<>();
}

/*------------------------------------------------------------------
 * PARSER RULES
 * TRADUCE L'ASSEMBLY IN NUMERI, NON STIAMO ANCORA ESEGUENDO.
 *------------------------------------------------------------------*/
   
assembly: instruction* EOF 	{ for (Integer j: labelRef.keySet())
                                // SOSTITUISCE IL RIFERIMENTO AD OGNI LABEL CON L'INDIRIZZO IN CUI È SALVATA QUELLA LABEL
								code[j]=labelDef.get(labelRef.get(j));
							} ;

instruction : 
        PUSH n=INTEGER   {code[i++] = PUSH; // PUSH DI INTEGER NELLO STACK E POST INCREMENTO DI i.
			              code[i++] = Integer.parseInt($n.text);}
	  | PUSH l=LABEL    {code[i++] = PUSH; 
	    		             labelRef.put(i++,$l.text);}  // PUSH DELL'INDIRIZZO PUNTATO DALLA LABEL SULLO STACK.
	  | POP		    {code[i++] = POP;}	// POP DEL TOP DELLO STACK.
	  | ADD		    {code[i++] = ADD;}
	  | SUB		    {code[i++] = SUB;} // POP DEI DUE VALORI v1 E v2 E PUSH DI v2-v1
	  | MULT	    {code[i++] = MULT;}
	  | DIV		    {code[i++] = DIV;} // POP DEI DUE VALORI v1 E v2 E PUSH DI v2/v1
	  | STOREW	  {code[i++] = STOREW;} //
	  | LOADW           {code[i++] = LOADW;} //
	  | l=LABEL COL     {labelDef.put($l.text,i);} // MEMORIZZA L'INDIRIZZO DELLA LABEL.
	  | BRANCH l=LABEL  {code[i++] = BRANCH; // SALTA ALL'ISTRUZIONE PUNTATA DALLA LABEL, EQUIVALENTE DEL JUMP IN ASSEMBLY C
                       labelRef.put(i++,$l.text);}
	  | BRANCHEQ l=LABEL {code[i++] = BRANCHEQ;
                        labelRef.put(i++,$l.text);}
	  | BRANCHLESSEQ l=LABEL {code[i++] = BRANCHLESSEQ;
                          labelRef.put(i++,$l.text);}
	  | JS              {code[i++] = JS;}		     //
	  | LOADRA          {code[i++] = LOADRA;}    //
	  | STORERA         {code[i++] = STORERA;}   //
	  | LOADTM          {code[i++] = LOADTM;}   
	  | STORETM         {code[i++] = STORETM;}   
	  | LOADFP          {code[i++] = LOADFP;}   //
	  | STOREFP         {code[i++] = STOREFP;}   //
	  | COPYFP          {code[i++] = COPYFP;}   //
	  | LOADHP          {code[i++] = LOADHP;}   //
	  | STOREHP         {code[i++] = STOREHP;}   //
	  | PRINT           {code[i++] = PRINT;}
	  | HALT            {code[i++] = HALT;}
	  ;
	  
/*------------------------------------------------------------------
 * LEXER RULES
 *------------------------------------------------------------------*/

PUSH	 : 'push' ; 	
POP	 : 'pop' ; 	
ADD	 : 'add' ;  	
SUB	 : 'sub' ;	
MULT	 : 'mult' ;  	
DIV	 : 'div' ;	
STOREW	 : 'sw' ; 	
LOADW	 : 'lw' ;	
BRANCH	 : 'b' ;	
BRANCHEQ : 'beq' ;	
BRANCHLESSEQ:'bleq' ;	
JS	 : 'js' ;	
LOADRA	 : 'lra' ;	
STORERA  : 'sra' ;	 
LOADTM	 : 'ltm' ;	
STORETM  : 'stm' ;	
LOADFP	 : 'lfp' ;	
STOREFP	 : 'sfp' ;	
COPYFP   : 'cfp' ;      
LOADHP	 : 'lhp' ;	
STOREHP	 : 'shp' ;	
PRINT	 : 'print' ;	
HALT	 : 'halt' ;	
 
COL	 : ':' ;
LABEL	 : ('a'..'z'|'A'..'Z')('a'..'z' | 'A'..'Z' | '0'..'9')* ;
INTEGER	 : '0' | ('-')?(('1'..'9')('0'..'9')*) ;

COMMENT : '/*' .*? '*/' -> channel(HIDDEN) ;

WHITESP  : (' '|'\t'|'\n'|'\r')+ -> channel(HIDDEN) ;

ERR	     : . { System.out.println("Invalid char: "+getText()+" at line "+getLine()); lexicalErrors++; } -> channel(HIDDEN); 

