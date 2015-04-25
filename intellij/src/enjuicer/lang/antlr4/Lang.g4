grammar Lang;

program: statement*;
statement: propertyAssign | assign | function | relation | relationCall;
assign: ID ASSIGN_OP expression;
function: ID DEFINE_OP parameters? expression;
relation: ID DEFINE_OP parameters? (statement | OPEN_BRA statement+ CLOSE_BRA);
relationCall: id OPEN_PAR (id (COMMA id)*)? CLOSE_PAR;
parameters: PIPE ID* PIPE;
expression: addExpression;
addExpression: mulExpression (ADD_OP mulExpression)*;
mulExpression: leafExpression (MUL_OP leafExpression)*;
leafExpression: 
    functionCall | property | id | number | string | array | block | embeddedExpression;
functionCall: id OPEN_PAR (expression (COMMA expression)*)? CLOSE_PAR;
property: target=id DOT name=id;
propertyAssign: target=id DOT name=id ASSIGN_OP expression;
id: ID;
number: NUMBER;
string: STRING;
array: OPEN_SQ (expression (COMMA expression)*)? CLOSE_SQ;
block: OPEN_BRA parameters? expression CLOSE_BRA;
embeddedExpression: OPEN_PAR expression CLOSE_PAR;

COMMA: ',';
OPEN_PAR: '(';
CLOSE_PAR: ')';
OPEN_BRA: '{';
CLOSE_BRA: '}';
OPEN_SQ: '[';
CLOSE_SQ: ']';
ADD_OP: '+' | '-';
MUL_OP: '*' | '/';
ASSIGN_OP: '=';
DEFINE_OP: '=>';
PIPE: '|';
DOT: '.';
COLON: ':';
fragment DIGIT: [0-9];
fragment LETTER: [A-Z]|[a-z];
ID: (LETTER | '_') (LETTER | '_' | DIGIT)*;
NUMBER: DIGIT+ (DOT DIGIT+)?;
STRING: '"' (EscapeSequence | ~[\\"])* '"';
fragment HexDigit: [0-9a-fA-F];
fragment EscapeSequence: '\\' [btnfr"'\\] | UnicodeEscape | OctalEscape;
fragment OctalEscape: '\\' [0-3] [0-7] [0-7] | '\\' [0-7] [0-7] | '\\' [0-7];
fragment UnicodeEscape: '\\' 'u' HexDigit HexDigit HexDigit HexDigit;

WS: [ \n\t\r]+ -> skip;
