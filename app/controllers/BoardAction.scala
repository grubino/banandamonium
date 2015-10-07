package controllers

import play.api.mvc.{Result, WrappedRequest, Request, ActionBuilder}

import scala.concurrent.Future


class BoardActionRequest[A](val gameId: String, val request: Request[A]) extends WrappedRequest[A](request)

object BoardAction extends ActionBuilder[BoardActionRequest] {
  override def invokeBlock[A](request: Request[A], block: (BoardActionRequest[A]) => Future[Result]): Future[Result] = ???
}
