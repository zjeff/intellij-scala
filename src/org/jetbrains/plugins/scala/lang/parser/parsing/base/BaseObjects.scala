package org.jetbrains.plugins.scala.lang.parser.parsing.base {

/**
 * User: Dmitry.Krasilschikov
 * Date: 17.10.2006
 * Time: 11:38:56
 */



/*
    StatementSeparator ::= NewLine | �;�
*/

object StatementSeparator {
  def parse(builder: PsiBuilder): Unit = {

    builder.getTokenType() match {
      case ScalaTokenTypes.tSEMICOLON => {
        val semicolonMarker = builder.mark()
        builder.advanceLexer

        semicolon.done(ScalaTokenTypes.tSEMICOLON)
      }

      case ScalaTokenTypes.tWHITE_SPACE_LINE_TERMINATE => {
        val lineTerminatorMarker = builder.mark()
        builder.advanceLexer

        lineTerminator.done(ScalaTokenTypes.tWHITE_SPACE_LINE_TERMINATE)
      }

      case _ => { builder.error("wrong statement separator")}
    }

  }
}

/*
    AttributeClause ::= �[� Attribute {�,� Attribute} �]� [NewLine]
*/

object AttributeClause {
  def parse(builder: PsiBuilder): Unit = {

    builder.getTokenType() match {
      //expected left square brace
      case ScalaTokenTypes.tLSQBRACKET => {
        val lsqbracketMarker = builder.mark()
        builder.advanceLexer
        lsqbracketMarker.done(ScalaTokenTypes.tLSQBRACKET)

        val attributeMarker = builder.mark()
        Attribute.parse(builder)
        attributeMarker.done(ScalaElementTypes.ATTRIBUTE)

        //possible attributes, separated by comma
        while (builder.getTokenType().equals(ScalaTokenTypes.tCOMMA)){
          val commaMarker = builder.mark()
          builder.advanceLexer
          commaMarker.done(ScalaTokenTypes.tCOMMA)

          val attributeMarker = builder.mark()
          Attribute.parse(builder)
          attributeMarker.done(ScalaElementTypes.ATTRIBUTE)
        }


        //expected right square brace
        if (builder.getTokenType().equals(ScalaTokenTypes.tRSQBRACKET)) {
          val rsqbracketMarker = builder.mark()
          builder.advanceLexer
          rsqbracketMarker.done(ScalaTokenTypes.tRSQBRACKET)

        } else {
          builder.error("expected ']'")
        }

        builder.getTokenType() match {
          //possible line terminator
          case ScalaTokenTypes.tLineTerminator => {
            val lineTerminatorMarker = builder.mark()
            builder.advanceLexer
            lineTerminatorMarker.done(ScalaTokenTypes.tLINE_TERMINATOR)
          }

          case => {}
        }
      }

      case _ => { builder.error("wrong statement separator")}
    }

  }

}

/*
    Attribute ::= Constr
*/

object Attribute {
  def parse(builder: PsiBuilder): Unit = {
    Constr.parse(builder)
  }
}

/*
    Constr ::= StableId [TypeArgs] {�(� [Exprs] �)�}
*/

object Constr {
  def parse(builder: PsiBuilder): Unit = {
    builder.getTokenType() match {
      case ScalaTokenTypes.tIDENTIFIER => {
        val stableIDMarker = builder.mark()

        //parse stable identifier
        StableID.parse(builder)

        stableIDMarker.done(ScalaElementTypes.STABLE_ID)

        builder.getTokenType() match {
          case ScalaTokenTypes.tLSQBRACKET => {
            val typeArgsMarker = builder.mark()

            TypeArgs.parse(builder)

            typeArgsMarker.done(ScalaElementTypes.TYPE_ARGS)

            //expect right closing bracket
            if (!builder.getTokenType().equals(ScalaTokenTypes.tRSQBRACKET)) {
              builder.error("epected ']'")
            }

             //possible left parenthis - begining of list epression
            while (builder.getTokenType().equals(ScalaTokenTypes.tLPARENTHIS)) {
                val exprInParenthisMarker = builder.mark()

                ExprInParenthis.parse(builder)

                exprInParenthisMarker.done(ScalaElementTypes.EXPESSION_LIST)

                if ( !builder.getTokenType().equals(ScalaTokenTypes.tRPARENTHIS) ) {
                  builder.error("expected ')'")
                }
            }
          }

          case _ => {}
        }
      }

      case _ => {builder.error("expected identifier")}
    }
  }

/*
    ExprInParenthis :== '(' [exprs] ')'
*/

  object ExprInParenthis {
    def parse(builder: PsiBuilder): Unit = {

      builder.getTokenType() match {
        case ScalaTokenTypes.tLPARENTHIS => {
          val lparenthisMarker = builder.mark()
          builder.advanceLexer
          lparenthisMarker.done(ScalaTokenTypes.tLPARENTHIS)

          builder.getTokenType() match {
            case ScalaTokenTypes.tINTEGER
               | ScalaTokenTypes.tFLOAT
               | ScalaTokenTypes.kTRUE
               | ScalaTokenTypes.kFALSE
               | ScalaTokenTypes.tCHAR
               | ScalaTokenTypes.kNULL
               | ScalaTokenTypes.tSTRING_BEGIN
               | ScalaTokenTypes.tPLUS
               | ScalaTokenTypes.tMINUS
               | ScalaTokenTypes.tTILDA
               | ScalaTokenTypes.tNOT
               | ScalaTokenTypes.tIDENTIFIER
               => {
               val exprsMarker = builder.mark()

               //parse expression list
               Exprs.parse()

               exprsMarker.done(ScalaElementTypes.EXPRESSION_LIST)
            }

            case _ => { builder.error("expected expression") }
          }

          if (builder.getTokenType().equals(ScalaTokenTypes.tRPARENTHIS)) {
            val rparenthisMarker = builder.mark()
            builder.advanceLexer
            rparenthisMarker.done(ScalaTokenTypes.tRPARENTHIS)

          } else {
            builder.error("expected ')'")
          }
        }
      }

    }
  }

 }

 /*
    TypeArgs :== '[' Types']'
 */

  object TypeArgs {

    def parse(builder: PsiBuilder): Unit = {
      builder.getTokenType() match {
        case ScalaTokenTypes.tLSQBRACKET => {
          val lsqbracketMarker = builder.mark()
          builder.advanceLexer
          lsqbracketMarker.done(ScalaTokenTypes.tLSQBRACKET)

          val typesMarker = builder.mark()
          Types.parse(builder)
          typesMarker.done(ScalaElementTypes.TYPE_ARGS)

          if (builder.getTokenType().equals(ScalaTokenTypes.tRSQBRACKET)) {
            val rsqbracketMarker = builder.mark()
            builder.advanceLexer
            rsqbracketMarker.done(ScalaTokenTypes.tRSQBRACKET)
          } else {
            builder.error("expected ']'")
          }

        }

      }
    }
  }

/*
    types :== Type {',' Type}
*/
  
  object Types {
    def parse(builder: PsiBuilder): Unit = {

    }
  }

/*
  Exprs ::= Expr {�,� Expr} [�:� �_� �*�]
*/
  object Exprs {
    def parse(builder: PsiBuilder): Unit = {
      builder.getTokenType() match {
        case ScalaTokenTypes.tINTEGER
           | ScalaTokenTypes.tFLOAT
           | ScalaTokenTypes.kTRUE
           | ScalaTokenTypes.kFALSE
           | ScalaTokenTypes.tCHAR
           | ScalaTokenTypes.kNULL
           | ScalaTokenTypes.tSTRING_BEGIN
           | ScalaTokenTypes.tPLUS
           | ScalaTokenTypes.tMINUS
           | ScalaTokenTypes.tTILDA
           | ScalaTokenTypes.tNOT
           | ScalaTokenTypes.tIDENTIFIER
           => {
           val exprMarker = builder.mark()
           Expr.parse(builder)
           exprMarker.done(ScalaElementTypes.EXPRESSION)

           while (builder.getTokenType().equals(ScalaTokenTypes.tCOMMA)){
             val commaMarker = builder.mark()
             bilder.advanceLexer
             commaMarker.done(scalaElementTypes.COMMA)

           //todo: add first(expression)
             builder.getTokenType() match {
               case ScalaTokenTypes.tINTEGER
                  | ScalaTokenTypes.tFLOAT
                  | ScalaTokenTypes.kTRUE
                  | ScalaTokenTypes.kFALSE
                  | ScalaTokenTypes.tCHAR
                  | ScalaTokenTypes.kNULL
                  | ScalaTokenTypes.tSTRING_BEGIN
                  | ScalaTokenTypes.tPLUS
                  | ScalaTokenTypes.tMINUS
                  | ScalaTokenTypes.tTILDA
                  | ScalaTokenTypes.tNOT
                  | ScalaTokenTypes.tIDENTIFIER
                  | ScalaTokenTypes.kIF
                  | ScalaTokenTypes.kTRY
                  => {
                  val exprMarker = builder.mark()
                  Expr.parse(builder)
                  exprMarker.done(ScalaElementTypes.EXPRESSION)
             }

             case _ => { builder.error("expected expression") }
           }
        }

        builder.getTokenType() match {
          case ScalaTokenTypes.tCOLON
             | ScalaTokenTypes.tUNDER
             | ScalaTokenTypes.tSTAR
             => {
             //todo
            }

         case _ => {}
        }

      }

      case _ => { builder.error("expected expression") }

    }
  }

/*
    Modifier ::= LocalModifier
    | override
    | private [ "[" id "]" ]
    | protected [ "[" id "]" ]
*/

  object Modifier {
    def parse(builder: PsiBuilder): Unit = {
      builder.getTokenTypes() match {
         case ScalaTokenTypes.kABSTRACT
            | ScalaTokenTypes.kFINAL
            | ScalaTokenTypes.kSEALED
            | ScalaTokenTypes.kIMPLICIT
            => {
            val localModifierMarker = builder.mark()
            LocalModifier.parse(builder)
            localModifierMarker.done(ScalaElementTypes.LOCAL_MODIFIER)
         }

        case ScalaTokenTypes.kOVERRIDE => {
          val overrideMarker = builder.mark()
          builder.advanceLexer
          overrideMarker.done(ScalaElementTypes.OVERRIDE)
        }

        case ScalaTokenTypes.kPRIVATE
           | ScalaTokenTypes.kPROTECTED
           => {
           val accessModifierMarker = bilder.mark()
           AccessModifier.parse(builder)
           accessModifierMarker.done(ScalaElementtypes.MODIFIER_ACCESS)
        }
      }
    }
  }

  object AccessModifier {
    def parse(builder: PsiBuilder): Unit = {
      builder.getTokenTypes() match {
        case ScalaTokenTypes.tLSQBRACKET => {
          val lsqbracketMarker = builder.mark()
          builder.advanceLexer
          lsqbracketMarker.done(ScalaElementTypes.LSQBRACKET)

          builder.getTokenTypes() match {
            case ScalaTokenTypes.tIDENTIFIER => {
              val idMarker = builder.mark()
              builder.advanceLexer
              idMarker.done(ScalaElementTypes.IDENTIFIER)
            }

            case _ => { builder.error("expected identifier") }
          }

          if ( !builder.getTokenTypes().equals(ScalaElementTypes.LSQBRACKET) ){
            builder.error("expected ']'")
          }
        }
      }
    }
  }


  object LocalModifier {
    def parse(builder: PsiBuilder): Unit = {
      case ScalaTokenTypes.kABSTRACT
         | ScalaTokenTypes.kFINAL
         | ScalaTokenTypes.kSEALED
         | ScalaTokenTypes.kIMPLICIT
         => {
           val localModifierMarker = builder.mark()
           builder.advanceLexer
           localModifierMarker.done(ScalaElementtypes.LOCAL_MODIFIER)
         }
    }
  }

}
}