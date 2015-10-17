package controllers

import javax.inject.Inject

import model._
import play.api.mvc._
import play.api.libs.json._
import play.modules.reactivemongo.{ReactiveMongoApi, ReactiveMongoComponents, MongoController}
import reactivemongo.api.{ReadPreference, DefaultDB, MongoConnection, MongoDriver}
import play.modules.reactivemongo.json.collection._
import play.modules.reactivemongo.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

class Application @Inject() (val reactiveMongoApi: ReactiveMongoApi)
  extends Controller with MongoController with ReactiveMongoComponents {

  import JsonFormatters._

  val boardsCollection: JSONCollection = db.collection[JSONCollection]("boards")
  val diceRollsCollection: JSONCollection = db.collection[JSONCollection]("diceRolls")
  val turnsCollection: JSONCollection = db.collection[JSONCollection]("turns")
  val r = new Random()

  private def dropBoardState(id: String): Future[Result] = {
    boardsCollection.remove(Json.obj("gameId" -> id)).flatMap {
      _ => diceRollsCollection.remove(Json.obj("gameId" -> id))
    }.flatMap {
      _ => turnsCollection.remove(Json.obj("gameId" -> id))
    }.flatMap {
      _ => Future.successful(NoContent)
    }
  }

  private def getCurrentRolls(id: String): Future[Option[DiceRolls]] = {
    diceRollsCollection.find(Json.obj("gameId" -> id)).sort(Json.obj("turnIndex" -> -1)).one[DiceRolls]
  }

  private def getCurrentBoard(id: String): Future[Option[Board]] = {
    boardsCollection.find(Json.obj("gameId" -> id)).sort(Json.obj("turnIndex" -> -1)).one[Board]
  }

  private def getBoardTurn(id: String, turnIndex: Int): Future[Option[Board]] = {
    boardsCollection.find(Json.obj("gameId" -> id, "turnIndex" -> turnIndex)).one[Board]
  }

  private def getAllBoards(id: String): Future[List[Board]] = {
    boardsCollection.find(Json.obj("gameId" -> id)).sort(Json.obj("turnIndex" -> -1)).cursor[Board](ReadPreference.primary).collect[List]()
  }

  private def getBoardState(id: String): Future[(Option[Board], Option[DiceRolls])] = {
    getCurrentBoard(id) zip getCurrentRolls(id)
  }

  private def newRoll(gameId: String, playerId: Int, turnIndex: Int, diceCount: Int): DiceRolls = {
    val diceRollsInts: List[Int] = (for (i <- 1 to diceCount;
                                         diceRoll = Math.abs(r.nextInt % 6) + 1) yield diceRoll).toList
    DiceRolls(gameId, turnIndex, diceRollsInts)
  }

  def roll(id: String, playerId: Int) = Action.async {
    getBoardState(id) flatMap {
      state =>
        // TODO - create unique index on {gameId: 1, turnIndex: -1}
        val maybeBoard = state._1
        val lastRoll = state._2
        maybeBoard match {
          case Some(board) =>
            lastRoll match {
              case Some(roll) =>
                if(board.currentPlayer != playerId) {
                  Future.successful(BadRequest("out of turn"))
                } else if(roll.turnIndex != board.turnIndex) {
                  val diceRolls = newRoll(id, board.currentPlayer, board.turnIndex, board.diceCount)
                  diceRollsCollection.insert(diceRolls).map {
                    lastError => Created(Json.toJson(diceRolls))
                  }
                } else {
                  Future.successful(Ok(Json.toJson(roll)))
                }
              case None =>
                val diceRolls = newRoll(id, board.currentPlayer, board.turnIndex, board.diceCount)
                diceRollsCollection.insert(diceRolls).map {
                  lastError => Created(Json.toJson(diceRolls))
                }
            }
          case None =>
            Future.successful(NotFound("board " + id + "doesn't exist"))
        }
    } recover {
      case t: Throwable => BadRequest("could not roll: "+t.getMessage)
    }
  }

  def getRolls(id: String) = Action.async {
    request =>
      getCurrentRolls(id) map {
        rolls => Ok(Json.toJson(rolls))
      }
  }

  def move(id: String, playerId: Int) = Action.async(parse.json) {
    request => request.body.validate[Turn].map {
      turn => getBoardState(id).map {
        state: (Option[Board], Option[DiceRolls]) =>
          val maybeBoard = state._1
          val maybeRolls = state._2
          maybeBoard match {
            case Some(board) =>
              maybeRolls match {
                case Some(rolls) => board.consumeDice (turn.moves, rolls.diceRolls)
                case None => throw new IllegalStateException("no rolls found");
              }
            case None =>
              throw new IllegalAccessException("no board found")
          }
      }.map {
        newBoard => boardsCollection.insert(newBoard).zip(turnsCollection.insert(turn))
      }.map {
        errorTuple => Ok(Json.toJson(turn))
      }.recover {
        case e: Throwable => BadRequest("could not advance board: "+e.getMessage)
      }
    }.getOrElse(Future.successful(BadRequest("invalid move object")))
  }

  def index(gameId: String, playerId: Int) = Action {
    Ok(views.html.banandamonium(gameId, playerId))
  }

  def healthcheck = Action {
    Ok("SUCCESS")
  }

  def dropBoard(id: String) = Action.async {
    dropBoardState(id)
  }

  def getBoard(id: String, turnIndex: Int) = Action.async {
    // TODO - create unique index on {gameId: 1, turnIndex: -1}
    getBoardTurn(id, turnIndex).map {
      case Some(b) => Ok(Json.toJson(b))
      case None => NotFound("board " + id + " at turn " + turnIndex + " not found")
    }
  }

  def getBoards(id: String) = Action.async {
    getAllBoards(id).map {
      boards =>
        Ok(Json.toJson(boards))
    }
  }

  def createBoard(id: String, playerCount: Int, diceCount: Int, maxStack: Int) = Action.async {
    val newBoard = Board(
      gameId = id,
      layers = LayerGenerator.generateLayers(playerCount),
      monkeyStarts = MonkeyStartGenerator.generateMonkeyStarts(playerCount, 7),
      maxStack = maxStack, diceCount = diceCount, currentPlayer = 0, turnIndex = 0,
      playerCount = playerCount,
      bananaCards = BananaCards(List(), List(), List()))
    boardsCollection.insert(newBoard).map {
      lastError => Created(Json.toJson(newBoard))
    }
  }
}