import controllers.Application
import org.specs2.mutable._
import org.specs2.runner._
import org.specs2.mock._
import org.junit.runner._
import play.api.Play.current
import play.api.test._
import play.api.test.Helpers._

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 * For more information, consult the wiki.
 */
@RunWith(classOf[JUnitRunner])
class ApplicationSpec extends Specification {

  "Application" should {

    "send 404 on a bad request" in {
      route(FakeRequest(GET, "/boum")) must beNone
    }

    "pass health check" in new WithApplication {
      val healthCheck = route(FakeRequest(GET, "/healthcheck")).get
      status(healthCheck) must equalTo(OK)
      contentType(healthCheck) must beSome.which(_ == "text/plain")
    }

    "render the board page" in {
      val home = controllers.Application.index("newBoard")(FakeRequest())
      status(home) must equalTo(OK)
      contentType(home) must beSome.which(_ == "text/html")
    }

    "call the /roll/id endpoint" in {
      val roll = controllers.Application.roll("newBoard")(FakeRequest())
      status(roll) must equalTo(CREATED)
      contentType(roll) must beSome.which(_ == "application/json")
    }

  }
}
