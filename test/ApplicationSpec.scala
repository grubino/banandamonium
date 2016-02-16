import controllers.Banandamonium
import org.specs2.mutable._
import org.specs2.runner._
import org.specs2.mock._
import org.junit.runner._
import play.api.Mode
import play.api.Play.current
import play.api.mvc.Results
import play.api.test._
import play.api.test.Helpers._
import play.modules.reactivemongo.ReactiveMongoApi
import play.api.inject.guice.GuiceApplicationBuilder
import play.modules.reactivemongo.json.collection.JSONCollection

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 * For more information, consult the wiki.
 */
@RunWith(classOf[JUnitRunner])
class ApplicationSpec extends PlaySpecification with Mockito {

  val mongoApi = mock[ReactiveMongoApi]
  val boardsCollection = mock[JSONCollection]
  val diceRollsCollection = mock[JSONCollection]
  val turnsCollection = mock[JSONCollection]
  val banandamonium = new Banandamonium(mongoApi)

  def setupMongo = {
    mongoApi.db.collection[JSONCollection]("boards") returns(boardsCollection)
    mongoApi.db.collection[JSONCollection]("diceRolls") returns(boardsCollection)
    mongoApi.db.collection[JSONCollection]("turns") returns(boardsCollection)
  }

  "Banandamonium" should {

    "send 404 on a bad request" in {
      route(FakeRequest(GET, "/boum")) must beNone
    }

    "Pass health check" in new WithApplication {
      val healthCheck = route(FakeRequest(GET, "/healthcheck")).get
      status(healthCheck) must equalTo(OK)
      contentType(healthCheck) must beSome.which(_ == "text/plain")
    }

    "Create a new board" in new WithApplication {
      setupMongo
      val home = banandamonium.createBoard("testBoard", 4, 2, 2)(FakeRequest());
      status(home) must equalTo(CREATED)
      contentType(home) must beSome.which(_ == "application/json")
    }

    "Create a Roll" in new WithApplication {
      val roll = banandamonium.roll("testBoard", 0)(FakeRequest())
      status(roll) must equalTo(CREATED)
      contentType(roll) must beSome.which(_ == "application/json")
    }

  }
}
