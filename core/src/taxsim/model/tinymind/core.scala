package taxsim.model.tinymind

import scala.collection.immutable

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

  type LabelParameterDouble
  type LabelParameterInt
  type LabelParameterBoolean

  final case class ParameterDouble  (val label : LabelParameterDouble, val value : Double)   extends Parameter[LabelParameterDouble]
  final case class ParameterInt     (val label : LabelParameterInt, val value : Int)         extends Parameter[LabelParameterInt]
  final case class ParameterBoolean (val label : LabelParameterBoolean, val value : Boolean) extends Parameter[LabelParameterBoolean]

  val parametersDouble  : Set[ParameterDouble]
  val parametersInt     : Set[ParameterInt]
  val parametersBoolean : Set[ParameterBoolean]

  lazy val doubles  : Map[LabelParameterDouble,Double]   = parametersDouble.map(p => (p.label, p.value)).toMap
  lazy val ints     : Map[LabelParameterInt,Int]         = parametersInt.map(p => (p.label, p.value)).toMap
  lazy val booleans : Map[LabelParameterBoolean,Boolean] = parametersBoolean.map(p => (p.label, p.value)).toMap

  trait Requirer[LD,LI,LB]:
    def requiresParametersDouble  : Set[LabelParameterDouble]
    def requiresParametersInt     : Set[LabelParameterInt]
    def requiresParametersBoolean : Set[LabelParameterBoolean]

  abstract class AverageIncomeAugmenter extends Function2[Group,Model,Double], Requirer;

  final case class Model(groups : Groups, augmenter : AverageIncomeAugmenter):
    val augmentedAverageIncome = groups.toSeq.map(g => (g, augmenter(g, this))).toMap
    val augmentedTotalIncome   = augmentedAverageIncome.map( (k,v) => (k, v * k.size) ).toMap


object SaezZucman2020ModelContext extends ModelContext:
  enum LPDouble:
    case TaxableRealizedGainsAugmentation // income stats exclude realized gains, but the tax base does in part include them

  import LPDouble.*

  override type LabelParameterDouble  = LPDouble
  override type LabelParameterInt     = Nothing
  override type LabelParameterBoolean = Nothing

  override val parametersDouble : Set[ParameterDouble] =
    Set(
      ParameterDouble(TaxableRealizedGainsAugmentation,0.366) 
    )
  override val parametersInt     : Set[ParameterInt]     = Set.empty
  override val parametersBoolean : Set[ParameterBoolean] = Set.empty

  object Augmenter extends AverageIncomeAugmenter:
    override def apply(g : Group, model : Model) = (1 + doubles(TaxableRealizedGainsAugmentation)) * g.averageIncome
    override def requiresParametersDouble  : Set[LabelParameterDouble]  = Set(TaxableRealizedGainsAugmentation)
    override def requiresParametersInt     : Set[LabelParameterInt]     = Set.empty
    override def requiresParametersBoolean : Set[LabelParameterBoolean] = Set.empty
