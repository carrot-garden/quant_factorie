package cc.factorie.bp

import collection.mutable.HashMap
import scala.math._
import cc.factorie._

/**
 * todo: we should define messages in a way that they are always
 * defined over Any, but return 0/-inf for most values. This
 * will reduce type clutter, and should be sufficient for
 * all operations we need.
 */
trait GenericMessage {
  self =>

  import StrictMath._

  def *(that: GenericMessage): GenericMessage

  def /(that: GenericMessage): GenericMessage

  def inverse: GenericMessage

  def deterministicValue: Option[Any]

  def unnormalized(value: Any) = exp(score(value))

  def probability(value: Any) = exp(score(value)) / Z

  def sample: Any

  def score(value: Any): Double

  def domain: Seq[Any]

  def defaultScore: Double = Double.NegativeInfinity

  def nonMerged: Iterable[Any] = domain

  def mergedSize: Long = (domain.size - nonMerged.size).toLong

  def Z: Double

  def renormalize = this

  //def normalize

  def entropy = {
    var result = 0.0
    for (x <- domain) {
      val prob = probability(x)
      result -= prob * log(prob)
    }
    result
  }

  def dynamicRange = {
    if (domain.size == 1) 0.0
    else {
      val ratios = for (i <- domain; j <- domain; if (i != j)) yield {
        val prob_i = probability(i)
        val prob_j = probability(j)
        prob_i / prob_j
      }
      val maxRatio = ratios.max
      if (!maxRatio.isNaN) assert(maxRatio >= 1.0)
      val dr = sqrt(maxRatio)
      if (dr.isNaN) Double.PositiveInfinity else dr
    }
  }

  def ranked: Seq[(Any, Double)] = domain.map(v => v -> score(v)).sortBy(-_._2)

  def isUniform = false

  def isDeterministic = false

  def map[A]: A = domain.maxBy(v => score(v)).asInstanceOf[A]
}

trait GenericUniformMessage extends GenericMessage {
  def defaultValue = null

  def Z = error("Can't sum over domain of the uniform message")

  def domain = error("Can't iterate over domain of the uniform message")

  def score(value: Any) = 0.0

  def deterministicValue = None

  def /(that: GenericMessage) = that.inverse

  def *(that: GenericMessage) = that

  def inverse = this

  override def toString = "Uniform"

  override def dynamicRange = 1.0
}

object UniformMessage extends GenericUniformMessage {

  def sample = null

  override def isUniform = true
}

class DiscreteMessage[Value](val scores: Seq[Double], _domain: Seq[Value]) extends GenericMessage {
  assert(!scores.exists(_.isNaN), "Nan in scores: " + scores)
  assert(!scores.exists(_.isPosInfinity), "Pos Inf in scores: " + scores)

  override def domain: Seq[Value] = _domain

  def sample = domain((0 until domain.length).sampleExpProportionally(i => scores(i)))

  override def renormalize = new DiscreteMessage(scores.map(_ - log(Z)), domain)

  override def *(that: GenericMessage) = that match {
    case m if (m.isUniform) => this
    case d: DiscreteDeterministicMessage[Value] => d * this
    case d: DiscreteMessage[Value] => new DiscreteMessage[Value](scores.zip(d.scores).map(pair => pair._1 + pair._2), domain)
    case _ => error("Need compatible messages")
  }

  override def /(that: GenericMessage) = that match {
    case m if (m.isUniform) => this
    case d: DiscreteDeterministicMessage[Value] => error("Cannot divide regular message with Deterministic Message")
    case d: DiscreteMessage[Value] => new DiscreteMessage[Value](scores.zip(d.scores).map(pair => pair._1 - pair._2), domain)
    case _ => error("Need compatible messages")
  }

  override def score(value: Any) = {
    value match {
      //      case i: Int => scores(i)
      case v: Value => scores(domain.indexOf(v))
      case _ => Double.NegativeInfinity
    }
  }

  override def entropy = {
    var result = 0.0
    for (i <- 0 until scores.size) {
      val score = scores(i)
      val prob = exp(score) / Z
      result -= prob * log(prob)
    }
    result
  }

  lazy val Z = scores.map(exp(_)).sum

  def deterministicValue: Option[Any] =
    if (scores.exists(s => s.isPosInfinity)) {
      domain.find(score(_) == Double.PositiveInfinity)
    } else if (scores.exists(s => s.isNegInfinity)) {
      domain.find(score(_) != Double.NegativeInfinity)
    } else None

  lazy val inverse = new DiscreteMessage[Value](scores.map(score => -score), domain)

  def defaultValue = domain.head

  override def toString: String = domain.map(x => "%5.5f/%f".format(probability(x), score(x))).mkString(",")

  override lazy val isDeterministic = scores.exists(s => s.isPosInfinity || s.isNegInfinity)

  override lazy val isUniform = scores.min == scores.max

}

class DiscreteDeterministicMessage[Value](val value: Value, _domain: Seq[Value])
      extends DiscreteMessage(_domain.map(d => if (d != value) Double.NegativeInfinity else 0.0), _domain) {
  override lazy val isDeterministic = true

  override lazy val deterministicValue: Option[Any] = Some(value)

  override def renormalize = this

  override def sample = value

  override def *(that: GenericMessage) = that match {
    case m if (m.isUniform) => this
    case d: DiscreteDeterministicMessage[Value] => {
      if (d.value != value)
        error("Cannot multiply %s with %s".format(value, d.value))
      else this
    }
    case d: DiscreteMessage[Value] => this
    case _ => error("Need compatible messages")
  }

  override def /(that: GenericMessage) = that match {
    case m if (m.isUniform) => this
    case d: DiscreteDeterministicMessage[Value] => {
      if (d.value != value)
        error("Cannot divide %s with %s".format(value, d.value))
      else UniformMessage
    }
    case d: DiscreteMessage[Value] => this // TODO: correct?
    case _ => error("Need compatible messages")
  }

  override def score(arg: Any) = {
    arg match {
      case m if (m == value) => 0.0
      case _ => Double.NegativeInfinity
    }
  }

  override def entropy = 0.0 // TODO correct?

  override def dynamicRange = Double.PositiveInfinity

  override lazy val Z = 1.0

  override def probability(other: Any) = other match {
    case v if (value == other) => 1.0
    case _ => 0.0
  }
}

abstract class Messages[T] extends HashMap[T, GenericMessage] {
  def this(pairs: (T, GenericMessage)*) {
    this ()
    this ++= pairs
  }

  def typed[M <: GenericMessage](t: T) = this(t).asInstanceOf[M]

  override def default(key: T): GenericMessage = UniformMessage

  def copy(messages: Messages[T]) = messages.foreach((p: (T, GenericMessage)) => this(p._1) = p._2)

  override def toString: String = map({
    case (v, m) => "%-10s %s".format(v, m)
  }).mkString("\n")
}

class FactorMessages extends Messages[Variable]

class VarMessages extends Messages[Factor]

/**
 * message between a variable and a factor.
 * also used to represent variable's product of incoming/outgoing message.
 *
 * Note that this implementation is cpu-optimized, not memory optimized. If you
 * want to reduce memory footprint, replace 'lazy val' with 'def'.
 */
case class MessageOperationNotSupported(thisType: GenericMessage, thatType: GenericMessage)
      extends RuntimeException("Messages of type %s can't be combined with messages of type %s".format(
        thisType.getClass.getSimpleName, thatType.getClass.getSimpleName))

case class CantCompute(function: String) extends RuntimeException("Can't compute " + function)