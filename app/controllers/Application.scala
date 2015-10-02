package controllers

import play.api._
import play.api.mvc._

object Application extends Controller {

  def index(playerCount: Int) = Action {
    Ok(views.html.banandamonium(playerCount))
  }

  def healthcheck = Action {
    Ok("SUCCESS")
  }

}