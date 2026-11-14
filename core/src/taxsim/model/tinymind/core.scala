package taxsim.model.tinymind

import scala.collection.immutable

import taxsim.model.{ExcessCurve,Schedule}

trait Parameter[L]:
  def label : L
  def value : Any

sealed trait Group:
  def size          : Int
  def minIncome     : Double
  def averageIncome : Double
  def totalIncome   : Double = size * averageIncome

object PercentileGroup:
  given Ordering[PercentileGroup] = Ordering.by[PercentileGroup,Tuple3[Double,Double,Int]]( pg => (pg.percentileLowerBound, pg.averageIncome, pg.size) )
final case class PercentileGroup( size : Int, minIncome : Double, averageIncome : Double, percentileLowerBound : Double ) extends Group
final case class TopGroup( size : Int, minIncome : Double, averageIncome : Double ) extends Group;

final case class Groups( percentileGroups : immutable.SortedSet[PercentileGroup], topGroup : TopGroup ):
  def contains( g : Group ) : Boolean =
    g match
      case pg : PercentileGroup => percentileGroups.contains(pg)
      case tg : TopGroup        => tg == topGroup
  def toSeq = percentileGroups.toSeq :+ topGroup

abstract class ModelContext:

  trait Label

  case class LabelNotImplemented private() extends Label

  /* Constants assumed by the model */
  type LabelParameterDouble  <: Label
  type LabelParameterInt     <: Label
  type LabelParameterBoolean <: Label

  /* Per-group data */
  type LabelDataDouble      <: Label
  type LabelDataInt         <: Label
  type LabelDataBoolean     <: Label
  type LabelDataExcessCurve <: Label

  /* Lever values the model assumes but which change change in countefactuals */
  type LabelLeverDouble   <: Label
  type LabelLeverInt      <: Label
  type LabelLeverBoolean  <: Label
  type LabelLeverSchedule <: Label

  val parametersDouble  : Map[LabelParameterDouble,Double]
  val parametersInt     : Map[LabelParameterInt,Int]
  val parametersBoolean : Map[LabelParameterBoolean,Boolean]

  trait Requirer:
    def requires : Set[Label]

  abstract class AverageIncomeAugmenter extends Function2[Group,Model,Double], Requirer;

  trait Instrument extends Requirer:
    def baselineTaxRate( model : Model )(g : Group) : Double
    def reformedTaxRate( model : Model, counterfactual : Counterfactual )(g : Group) : Double
    def deltaTaxRate( model : Model, counterfactual : Counterfactual )(g : Group) : Double

  case class CompoundInstrument(instruments : Set[Instrument]) extends Instrument:
    override def baselineTaxRate( model : Model )(g : Group)                                  : Double = instruments.foldLeft(0.0d)((accum,next) => accum + next.baselineTaxRate(model)(g))
    override def reformedTaxRate( model : Model, counterfactual : Counterfactual )(g : Group) : Double = instruments.foldLeft(0.0d)((accum,next) => accum + next.reformedTaxRate(model,counterfactual)(g))
    override def deltaTaxRate( model : Model, counterfactual : Counterfactual )(g : Group)    : Double = instruments.foldLeft(0.0d)((accum,next) => accum + next.deltaTaxRate(model,counterfactual)(g))
    override def requires : Set[Label] = instruments.foldLeft(Set.empty[Label])((accum,next) => accum ++ next.requires)

  final case class Model(
    groups          : Groups,
    augmenter       : AverageIncomeAugmenter,
    dataDouble      : Map[LabelDataDouble,Map[Group,Double]],
    dataInt         : Map[LabelDataInt,Map[Group,Int]],
    dataBoolean     : Map[LabelDataBoolean,Map[Group,Boolean]],
    dataExcessCurve : Map[LabelDataExcessCurve,Map[Group,ExcessCurve]],
    leverDouble     : Map[LabelLeverDouble,Map[Group,Double]],
    leverInt        : Map[LabelLeverInt,Map[Group,Int]],
    leverBoolean    : Map[LabelLeverBoolean,Map[Group,Boolean]],
    leverSchedule   : Map[LabelLeverSchedule,Map[Group,Schedule]],
  ):
    val augmentedAverageIncome = groups.toSeq.map(g => (g, augmenter(g, this))).toMap
    val augmentedTotalIncome   = augmentedAverageIncome.map( (k,v) => (k, v * k.size) ).toMap

  final case class Counterfactual(
    leverDouble     : Map[LabelLeverDouble,Map[Group,Double]],
    leverInt        : Map[LabelLeverInt,Map[Group,Int]],
    leverBoolean    : Map[LabelLeverBoolean,Map[Group,Boolean]],
    leverSchedule   : Map[LabelLeverSchedule,Map[Group,Schedule]],
  )

object SaezZucman2020Context extends ModelContext:
  enum LPDouble extends Label:
    case TaxableRealizedGainsAugmentationFactor // income stats exclude realized gains, but the tax base does in part include them

  import LPDouble.*

  /* Constants assumed by the model */
  override type LabelParameterDouble  = LPDouble
  override type LabelParameterInt     = LabelNotImplemented
  override type LabelParameterBoolean = LabelNotImplemented

  /* Per-group data */
  override type LabelDataDouble      = LabelNotImplemented
  override type LabelDataInt         = LabelNotImplemented
  override type LabelDataBoolean     = LabelNotImplemented
  override type LabelDataExcessCurve = LabelNotImplemented

  /* Lever values the model assumes but which change change in countefactuals */
  override type LabelLeverDouble   = LabelNotImplemented
  override type LabelLeverInt      = LabelNotImplemented
  override type LabelLeverBoolean  = LabelNotImplemented
  override type LabelLeverSchedule = LabelNotImplemented

  override val parametersDouble = Map(
    TaxableRealizedGainsAugmentationFactor -> 1.366
  )
  override val parametersInt     = Map.empty
  override val parametersBoolean = Map.empty

  object Augmenter extends AverageIncomeAugmenter:
    override def apply(g : Group, model : Model) = (parametersDouble(TaxableRealizedGainsAugmentationFactor)) * g.averageIncome
    override def requires : Set[Label] = Set(TaxableRealizedGainsAugmentationFactor)
