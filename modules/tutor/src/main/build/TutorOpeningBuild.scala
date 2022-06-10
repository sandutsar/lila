package lila.tutor
package build

import lila.game.Game
import chess.Color
import chess.opening.{ FullOpening, FullOpeningDB }
import chess.format.{ FEN, Forsyth }

case class TutorOpeningBuild(ply: Int, games: NbGames, moves: NbMoves)

object TutorOpeningBuild {

  type OpeningMap = Map[FullOpening, TutorOpeningBuild]

  def aggregate(openings: Color.Map[OpeningMap], richPov: RichPov): Color.Map[OpeningMap] = {

    import richPov._

    val opening = pov.game.variant.standard ??
      replay.view
        .drop(1)
        .takeWhile(_._1.board.actors.size > 16)
        .foldRight(none[FullOpening.AtPly]) {
          case (sit, None) => FullOpeningDB.findByFen(FEN(Forsyth exportStandardPositionTurnCastlingEp sit))
          case (_, found)  => found
        }

    opening.fold(openings) { op =>
      openings.update(
        pov.color,
        _.updatedWith(op.opening) { opt =>
          val prev = opt | TutorOpeningBuild(op.ply, NbGames(0), NbMoves(0))
          prev
            .copy(
              ply = prev.ply atMost op.ply,
              games = prev.games + 1,
              moves = prev.moves + op.ply / 2
            )
            .some
        }
      )
    }
  }

  def toReport(openings: Color.Map[OpeningMap]): TutorOpeningReport.OpeningMap = openings map toReport

  def toReport(openings: OpeningMap): TutorOpeningReport.ColorOpenings = {

    def familyOf(op: FullOpening) = op.name.takeWhile(_ != ':')
    val families: List[(String, List[(FullOpening, TutorOpeningBuild)])] =
      openings
        .groupBy(op => familyOf(op._1))
        .map(x => x._1 -> x._2.toList)
        .toList
        .sortBy(-_._2.map(_._2.games.value).sum)
        .take(10)

    // def variationOf(op: FullOpening) = op.name.takeWhile(_ != ',')
    // val variations: List[(String, Int)] = openings.values
    //   .foldLeft(Map.empty[String, Int]) { case (vars, op) =>
    //     vars.updatedWith(variationOf(op.opening))(nb => Some(~nb + 1))
    //   }
    //   .toList
    //   .sortBy(-_._2)
    // .takeWhile(_._2 >= threshold)

    // val familySet = families.map(_._1).toSet

    families.flatMap { case (_, builds) =>
      builds.sortBy(_._2.ply).headOption map { case (opening, _) =>
        TutorOpeningReport(
          opening = opening,
          games = NbGames(builds.map(_._2.games.value).sum),
          moves = NbMoves(builds.map(_._2.moves.value).sum)
        )
      }
    }
  }
}