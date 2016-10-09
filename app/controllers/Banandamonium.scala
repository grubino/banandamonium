package controllers

import javax.inject.Inject

import jp.t2v.lab.play2.auth.{AuthElement, LoginLogout}
import model._
import model.auth.BananaAuthConfig
import play.api.mvc._
import play.api.libs.json._
import play.modules.reactivemongo.{MongoController, ReactiveMongoApi, ReactiveMongoComponents}
import reactivemongo.api.ReadPreference
import reactivemongo.api.commands.{DefaultWriteResult, UpdateWriteResult}
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.{Ascending, Descending}
import reactivemongo.play.json.collection.JSONCollection
import reactivemongo.play.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.Random

class Banandamonium @Inject()(val reactiveMongoApi: ReactiveMongoApi)
  extends Controller with MongoController with ReactiveMongoComponents
    with BananaAuthConfig with LoginLogout with AuthElement {

  import JsonFormatters._

  override val mongoApi = reactiveMongoApi
  override def resolveUser(id: String)(implicit ctx: ExecutionContext): Future[Option[Player]] = {
    playerCollection.find(Json.obj("name" -> id)).one[Player]
  }
  override def loginSucceeded(request: RequestHeader)(implicit ctx: ExecutionContext): Future[Result] = {
    Future.successful(Ok)
  }

  override def logoutSucceeded(request: RequestHeader)(implicit ctx: ExecutionContext): Future[Result] = {
    Future.successful(NoContent)
  }

  override def authenticationFailed(request: RequestHeader)(implicit ctx: ExecutionContext): Future[Result] = {
    Future.successful(Unauthorized)
  }

  def createUser = Action.async(parse.json) { implicit request: Request[JsValue] =>
    val newPlayer = request.body.as[Player]
    playerCollection.insert(newPlayer).map {
      case DefaultWriteResult(true, _, _, _, _, _) => Ok(Json.toJson(newPlayer))
      case err => InternalServerError(Json.obj("error" -> s"there was an error: ${err.errmsg}"))
    }.recover {
      case e: Throwable => InternalServerError(Json.obj("error" -> e.getMessage))
    }
  }

  def generateToken: String = java.util.UUID.randomUUID.toString
  def authenticate = Action.async(parse.json) { implicit request: Request[JsValue] =>
    val loginInfo = request.body
    val userName = (loginInfo \ "userName").as[String]
    val password = (loginInfo \ "password").as[String]
    playerCollection.find(Json.obj("name" -> userName)).one[Player].flatMap { playerMaybe =>
      playerMaybe match {
        case Some(player) =>
          // todo - crypto hash the password
          if(player.password == password) {
            val token = generateToken
            playerCollection.update(
              Json.obj("name" -> player.name), Json.obj("$push" -> Json.obj("tokens" -> token))).flatMap { _ =>
                Future.successful(Ok(Json.obj("token" -> token)))
            }
          } else {
            Future.successful(Unauthorized)
          }
        case None => Future.successful(NotFound)
      }
    }.recover {
      case e: Throwable => InternalServerError(Json.obj("error" -> e.getMessage))
    }
  }

  def loginView = Action {
    Ok("under construction")
  }

  lazy val mongodb = {
    val _db = Await.result(database, 30 seconds)
    _db
  }
  def boardsCollection: JSONCollection = mongodb.collection[JSONCollection]("boards")
  lazy val diceRollsCollection: JSONCollection = {
    val _diceRollsCollection = mongodb.collection[JSONCollection]("diceRolls")
    _diceRollsCollection.indexesManager.ensure(Index(Seq(("turnIndex", Descending), ("gameId" -> Descending))))
    _diceRollsCollection
  }
  lazy val turnsCollection: JSONCollection = {
    val _turnsCollection = mongodb.collection[JSONCollection]("turns")
    _turnsCollection.indexesManager.ensure(
      Index(Seq(("turnIndex", Descending), ("gameId" -> Descending)), unique = true))
    _turnsCollection
  }
  lazy val playerCollection: JSONCollection = {
    val _playerCollection = mongodb.collection[JSONCollection]("players")
    _playerCollection.indexesManager.ensure(Index(Seq(("name", Ascending)), unique = true))
    _playerCollection
  }
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

  private def gameParticipant(boardId: String)(account: User): Future[Boolean] = {
    getCurrentBoard(boardId).map(boardMaybe =>
      boardMaybe.map(board =>
        account.gameTokens.map(userTokens => (board.playerTokens.toSet intersect userTokens.toSet).nonEmpty).getOrElse(false)).getOrElse(false))
  }

  def roll(id: String, playerId: Int) = AsyncStack(AuthorityKey -> gameParticipant(id)) { implicit request =>
    getBoardState(id) flatMap {
      state =>
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

  def createBoardView(playerId: Int) = Action {
    Ok("under construction")
  }

  def healthcheck = Action {
    Ok("SUCCESS")
  }

  def dropBoard(id: String) = Action.async {
    dropBoardState(id)
  }

  def getTokenBoards = Action.async { implicit request =>
    val token = request.headers.get("Authorization").getOrElse("")
    playerCollection.find(Json.obj("tokens" -> Json.obj("$elemMatch" -> Json.obj("$eq" -> token)))).one[Player].flatMap { player =>
      val gameTokens = player.flatMap(_.gameTokens).getOrElse(List())
      val boardsFuture = boardsCollection.find(
        Json.obj("tokens" ->
          Json.obj("$elemMatch" ->
            Json.obj("$in" -> gameTokens)))).sort(Json.obj("turnIndex" -> -1)).cursor[Board](ReadPreference.primary).collect[List]()
      boardsFuture.map(boards => Ok(Json.toJson(boards)))
    }
  }

  def getBoard(id: String, turnIndex: Int) = Action.async {
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

  private def isValidToken(user: User): Future[Boolean] = {
    Future.successful(true)
  }
  def createBoard(id: String,
                  playerCount: Int,
                  diceCount: Int,
                  maxStack: Int) = AsyncStack(AuthorityKey -> isValidToken) { implicit request =>
    val newBoard = Board(
      gameId = id,
      List[String](),
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