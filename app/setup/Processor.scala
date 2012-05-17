package lila
package setup

import http.Context
import game.{ DbGame, GameRepo, Pov }
import chess.{ Game, Board }
import ai.Ai

import scalaz.effects._

final class Processor(
    configRepo: UserConfigRepo,
    gameRepo: GameRepo,
    timelinePush: DbGame ⇒ IO[Unit],
    ai: () ⇒ Ai) {

  def ai(config: AiConfig)(implicit ctx: Context): IO[Pov] = for {
    _ ← ctx.me.fold(
      user ⇒ configRepo.update(user)(_ withAi config),
      io()
    )
    pov = config.pov
    game = pov.game
    _ ← gameRepo insert game
    _ ← game.variant.standard.fold(io(), gameRepo saveInitialFen game)
    _ ← timelinePush(game)
    pov2 ← game.player.isHuman.fold(
      io(pov),
      for {
        aiResult ← ai()(game) map (_.err)
        (newChessGame, move) = aiResult
        progress = game.update(newChessGame, move)
        _ ← gameRepo save progress
      } yield pov withGame progress.game
    )
  } yield pov2
}
