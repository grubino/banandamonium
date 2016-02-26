package model

import scala.util.parsing.combinator.{RegexParsers, JavaTokenParsers}

/**
  * Created by greg on 2/23/16.
  */
class MonkeyTalk(b: Board, sym: Map[String, Int]) extends RegexParsers {

  val symTable: Map[String, Int] = sym + ("your" -> b.currentPlayer)
  val board = b

  def monkeyExpr: Parser[Board] =
    rollAgain | moveMonkeys | slideMonkeys | swapMonkeys

  def swapMonkeys: Parser[Board] =
    "swap"~monkeyDescriptor~"with"~monkeyDescriptor ^^ {
      case s~firstDesc~w~secondDesc =>
        board.swapMonkeys(
          (symTable.getOrElse(firstDesc._1, throw new IllegalArgumentException),
            symTable.getOrElse(firstDesc._2, throw new IllegalArgumentException),
            symTable.getOrElse(firstDesc._3, throw new IllegalArgumentException)),
          (symTable.getOrElse(secondDesc._1, throw new IllegalArgumentException),
            symTable.getOrElse(secondDesc._2, throw new IllegalArgumentException),
            symTable.getOrElse(secondDesc._3, throw new IllegalArgumentException)))
    }

  def moveMonkeys: Parser[Board] =
    "move"~(repsep(monkeyMove, ",")) ^^ {
      case m~moveList => board.makePossibleMoves(moveList.flatten)
    }

  def wholeNumber = """-?[1-9]+""".r ^^ {_.toInt}

  def monkeyMove =
    "("~monkeyDescriptor~","~wholeNumber~")" ^^ {
      case oParen~descriptor~comma~distance~cParen =>
        descriptor._2 match {
          case "*" => board.allMove(symTable(descriptor._1), distance)
          case _ =>
            List(Move(
            symTable.getOrElse(descriptor._1, throw new IllegalArgumentException),
            Some(symTable.getOrElse(descriptor._2, throw new IllegalArgumentException)),
            Some(symTable.getOrElse(descriptor._3, throw new IllegalArgumentException)),
            descriptor._4, (for (i <- 1 to descriptor._4) yield distance).toList))
        }
    }

  def ident: Parser[String] = """[a-zA-Z]+""".r

  def monkeyDescriptor: Parser[(String, String, String, Int)] =
    ident~":"~(ident|"*")~":"~(ident|"*")~":"~wholeNumber ^^ {
      case monkeyId~sep1~layerId~sep2~placeId~sep3~count =>
        (monkeyId, layerId, placeId, count.toInt)
    }

  private def substitute(m: (String, String, String, Int)): (Int, Int, Int, Int) = {
    (symTable.getOrElse(m._1, throw new IllegalArgumentException),
      symTable.getOrElse(m._2, throw new IllegalArgumentException),
      symTable.getOrElse(m._3, throw new IllegalArgumentException),
      m._4)
  }
  def slideMonkeys: Parser[Board] =
    "slide"~repsep(monkeyDescriptor, ",")~("up"|"down") ^^ {
      case sl~monkeys~"up" => board.slideMonkeysUp(monkeys.map(substitute))
      case sl~monkeys~"down" => board.slideMonkeysDown(monkeys.map(substitute))
    }

  def rollAgain: Parser[Board] = """roll again""".r ^^ {
    x => board
  }
}

