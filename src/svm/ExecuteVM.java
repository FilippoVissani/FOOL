package svm;
public class ExecuteVM {
    
    public static final int CODESIZE = 10000;
    public static final int MEMSIZE = 10000;
    
    private int[] code;
    private int[] memory = new int[MEMSIZE];

    // PUNTA ALL'ISTRUZIONE DA ESEGUIRE (CODE)
    private int ip = 0;
    // STACK POINTER, PUNTA AL TOP DELLO STACK (MEMORY)
    // LO STACK VIENE MEMORIZZATO A PARTIRE DALL'INDIRIZZO PIÙ ALTO DELLA MEMORIA
    private int sp = MEMSIZE;
    // HEAP POINTER, PARTE DALLA PARTE BASSA DELLA MEMORIA
    private int hp = 0;
    // FRAME POINTER, UTILIZZATO PER PUNTARE PARTI DI STACK
    private int fp = MEMSIZE;
    // RETURN ADDRESS
    private int ra;
    // REGISTRO TEMPORANEO
    private int tm;
    
    public ExecuteVM(int[] code) {
      this.code = code;
    }
    
    public void cpu() {
      while ( true ) {
        int bytecode = code[ip++]; // FETCH DELL'ISTRUZIONE DA ESEGUIRE
        int v1,v2;
        int address;
        switch ( bytecode ) {
          case SVMParser.PUSH:
            push( code[ip++] );
            break;
          case SVMParser.POP:
            pop();
            break;
          case SVMParser.ADD :
            v1=pop();
            v2=pop();
            push(v2 + v1);
            break;
          case SVMParser.MULT :
            v1=pop();
            v2=pop();
            push(v2 * v1);
            break;
          case SVMParser.DIV :
            v1=pop();
            v2=pop();
            push(v2 / v1);
            break;
          case SVMParser.SUB :
            v1=pop();
            v2=pop();
            push(v2 - v1);
            break;
          case SVMParser.STOREW : // METTE UN VALORE IN UN INDIRIZZO DI MEMORIA
            address = pop();
            memory[address] = pop();    
            break;
          case SVMParser.LOADW : // POP DI UN INDIRIZZO E PUSH SULLO STACK DEL VALORE PUNTATO DA QUELL'INDIRIZZO
            push(memory[pop()]);
            break;
          case SVMParser.BRANCH : 
            address = code[ip];
            ip = address;
            break;
          case SVMParser.BRANCHEQ :
            address = code[ip++];
            v1=pop();
            v2=pop();
            if (v2 == v1) ip = address;
            break;
          case SVMParser.BRANCHLESSEQ :
            address = code[ip++];
            v1=pop();
            v2=pop();
            if (v2 <= v1) ip = address;
            break;
          case SVMParser.JS : // JUMP SUBROUTINE, ESEGUE UN SALTO SU UNA SUBROUTINE E SETTA RA AL PUNTO SUCCESSIVO ALLA CHIAMATA
            address = pop();
            ra = ip;
            ip = address;
            break;
         case SVMParser.STORERA : //
            ra=pop();
            break;
         case SVMParser.LOADRA : //
            push(ra);
            break;
         case SVMParser.STORETM : 
            tm=pop();
            break;
         case SVMParser.LOADTM : 
            push(tm);
            break;
         case SVMParser.LOADFP : //
            push(fp);
            break;
         case SVMParser.STOREFP : //
            fp=pop();
            break;
         case SVMParser.COPYFP : // COPIA SP IN FP
            fp=sp;
            break;
         case SVMParser.STOREHP : //
            hp=pop();
            break;
         case SVMParser.LOADHP : //
            push(hp);
            break;
         case SVMParser.PRINT :
            System.out.println((sp<MEMSIZE)?memory[sp]:"Empty stack!");
            break;
         case SVMParser.HALT :
            return;
        }
      }
    } 
    
    private int pop() {
      return memory[sp++];
    }
    
    private void push(int v) {
      memory[--sp] = v;
    }
    
}