package controllers

import java.util.concurrent.TimeUnit

import model._
import model.Board._
import play.api.mvc._
import play.api.Play.current
import play.api.libs.json._
import play.modules.reactivemongo.{ReactiveMongoApi, ReactiveMongoComponents, MongoController}
import reactivemongo.api.gridfs.GridFS
import reactivemongo.api.{ReadPreference, DefaultDB, MongoConnection, MongoDriver}
import play.modules.reactivemongo.json.collection._
import play.modules.reactivemongo.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.util.Random

object Application extends Controller with MongoController with ReactiveMongoComponents {

  import JsonFormatters._

  val reactiveMongoApi: ReactiveMongoApi = new ReactiveMongoApi {
    override def driver: MongoDriver = new MongoDriver
    override def gridFS: GridFS[JSONSerializationPack.type] = new GridFS[JSONSerializationPack.type](db)
    override def db: DefaultDB = DefaultDB(play.Play.application().configuration().getString("mongo.dbname"), connection)
    override def connection: MongoConnection = driver.connection(List(play.Play.application().configuration().getString("mongo.host")+":27017"))
  }

  val boardsCollection: JSONCollection = db.collection[JSONCollection]("boards")
  val diceRollsCollection: JSONCollection = db.collection[JSONCollection]("diceRolls")
  val turnsCollection: JSONCollection = db.collection[JSONCollection]("turns")
  val r = new Random()

  private def getCurrentRolls(id: String): Future[List[DiceRolls]] = {
    diceRollsCollection.find(Json.obj("gameId" -> id)).cursor[DiceRolls](ReadPreference.primary).collect[List]()
  }

  private def getCurrentBoard(id: String): Future[Board] = {
    boardsCollection.find(Json.obj("gameId" -> id)).sort(Json.obj("turnIndex" -> -1)).one[Board].map {
      case Some(board) => board
    }
  }

  private def getBoardState(id: String): Future[(Board, List[DiceRolls])] = {
    getCurrentBoard(id) zip getCurrentRolls(id)
  }

  def roll(id: String) = Action.async {
    getCurrentBoard(id) flatMap {
      board =>
        // TODO - create unique index on {gameId: 1, turnIndex: -1}
        val diceRollsInts: List[Int] = (for(i <- 1 to board.diceCount; diceRoll = Math.abs(r.nextInt % 6) + 1) yield diceRoll).toList
        val diceRolls = DiceRolls(id, board.turnIndex, diceRollsInts)
        val insertFuture = diceRollsCollection.insert(diceRolls).map {
          lastError => Created(Json.toJson(diceRolls))
        }
        insertFuture
    }
  }

  def getRolls(id: String) = Action.async {
    request =>
      getCurrentRolls(id) map {
        rolls => Ok(Json.toJson(rolls))
      }
  }

  def move(id: String) = Action.async(parse.json) {
    request => request.body.validate[Turn].map {
      turn => getBoardState(id).map {
        state: (Board, List[DiceRolls]) =>
          val board = state._1
          val rolls = state._2
          board.consumeDice(turn.moves, rolls.flatMap(r => r.diceRolls))
      }.map {
        newBoard => boardsCollection.insert(newBoard).zip(turnsCollection.insert(turn))
      }.map {
        errorTuple => Ok(Json.toJson(turn))
      }.recover {
        case e: Throwable => BadRequest("could not advance board: "+e.getMessage)
      }
    }.getOrElse(Future.successful(BadRequest("invalid move object")))
  }

  def index(playerCount: Int) = Action {
    Ok(views.html.banandamonium(playerCount))
  }

  def healthcheck = Action {
    Ok("SUCCESS")
  }

  def getBoard(id: String) = Action.async {
    // TODO - create unique index on {gameId: 1, turnIndex: -1}
    boardsCollection.find(Json.obj("gameId" -> id)).sort(Json.obj("turnIndex" -> -1)).one[Board].map {
      case Some(b) => Ok(Json.toJson(b))
      case None => NotFound("board " + id + " not found")
    }
  }

  def createBoard() = Action.async(parse.json) {
    request =>
      request.body.validate[Board].map {
        newBoard =>
          // TODO - create unique index on {gameId: 1, turnIndex: -1}
          boardsCollection.insert(newBoard).map {
            lastError => Created(Json.toJson(newBoard))
          }
      }.getOrElse(Future.successful(BadRequest("invalid input")))
  }
}