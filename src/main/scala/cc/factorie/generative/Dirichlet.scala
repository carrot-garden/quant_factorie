/* Copyright (C) 2008-2010 Univ of Massachusetts Amherst, Computer Science Dept
   This file is part of "FACTORIE" (Factor graphs, Imperative, Extensible)
   http://factorie.cs.umass.edu, http://code.google.com/p/factorie/
   This software is provided under the terms of the Eclipse Public License 1.0
   as published by http://www.opensource.org.  For further information,
   see the file `LICENSE.txt' included with this distribution. */

package cc.factorie.generative
import cc.factorie._

// Proportions ~ Dirichlet(Proportions, Precision)

trait Dirichlet extends Proportions with GeneratedVariable with CollapsibleParameter {
  /** Return the mean of the Dirichlet distribution from which this Proportions was generated.
      Note that the returned object may be a temporary one generated for the return value, 
      and may not have this Proportions as a child. */
  def mean: Proportions
  def precision: RealVarParameter
  def parents = List(mean, precision)
  def pr = math.exp(logpr)
  override def logpr: Double = logprFrom(mean, precision)
  def logprFrom(mean:Proportions, precision:RealVar): Double = {
    def alpha(index:Int): Double = mean(index) * precision.doubleValue
    require(mean.length == this.length)
    var result = Maths.logGamma(precision.doubleValue)
    forIndex(length)((i:Int) => result -= Maths.logGamma(alpha(i)))
    forIndex(length)((i:Int) => result += (alpha(i) - 1.0) * math.log(pr(i)))
    assert(result == result, "mean="+mean.toList+" precision="+precision.doubleValue+" alpha="+alphas.toList+" p="+this.toList) // check for NaN
    result
  }
  override def logprFrom(parents:Seq[Parameter]) = parents match {
    case Seq(mean:Proportions, precision:RealVar) => logprFrom(mean, precision)
  }
  def prFrom(parents:Seq[Parameter]) = math.exp(logprFrom(parents))
  def alpha(index:Int): Double = mean(index) * precision.doubleValue
  def alphas: Seq[Double] = mean.map(_ * precision.doubleValue)
  type CollapsedType <: DirichletMultinomial
}

trait MutableDirichlet extends MutableProportions with Dirichlet {
  def sampleFrom(mean:Proportions, precision:RealVar, children:Iterable[DiscreteVar] = Nil)(implicit d:DiffList): Unit = {
    set(Dirichlet.sampleFrom(mean, precision, children))
  }
  def sample(implicit d:DiffList): Unit = sampleFrom(mean, precision)
  def sampleFrom(parents:Seq[Variable])(implicit d:DiffList) = parents match {
    case Seq(mean:Proportions, precision:RealVarParameter) => sampleFrom(mean, precision)
  }
}

object Dirichlet {
  def sampleFrom(mean:Proportions, precision:RealVar, children:Iterable[DiscreteVar] = Nil): Array[Double] = {
    def alpha(index:Int): Double = mean(index) * precision.doubleValue
    var norm = 0.0
    val p = new Array[Double](mean.length)
    val c = new Array[Double](mean.length)
    for (child <- children) c(child.intValue) += 1.0
    forIndex(mean.length)(i => {
      p(i) = Maths.nextGamma(alpha(i) + c(i), 1)(cc.factorie.random)
      if (p(i) <= 0.0) p(i) = 0.0001
      norm += p(i)
    })
    forIndex(mean.length)(i => p(i) /= norm)
    p
  }
}

/** Proportions, Dirichlet-distributed, with dense separate values for all dimensions. */
class DenseDirichlet(initialMean:Proportions, initialPrecision:RealVarParameter, p:Seq[Double] = Nil) extends DenseProportions(if (p.length == 0) initialMean.asSeq else p) with MutableDirichlet {
  def this(size:Int, alpha:Double) = this(new UniformProportions(size), new RealConstantParameter(alpha * size), Nil)
  protected val meanRef: ParameterRef[Proportions,Dirichlet] = new ParameterRef(initialMean, this)
  protected val precisionRef = new ParameterRef(initialPrecision, this)
  def mean = meanRef.value
  def mean_=(newMean:Proportions)(implicit d:DiffList): Unit = meanRef.set(newMean)
  def precision = precisionRef.value
  def precision_=(newPrecision:RealVarParameter)(implicit d:DiffList): Unit = precisionRef.set(newPrecision)
  override def parentRefs = List(meanRef, precisionRef)
  def ~(mean:Proportions, precision:RealVarParameter): this.type = { mean_=(mean)(null); precision_=(precision)(null); this }
  type CollapsedType = DenseDirichletMultinomial
  def newCollapsed = new DenseDirichletMultinomial(mean, precision)
}

// TODO Perhaps all Dirichlet* classes should be re-implemented in terms of "alpha" instead of "precision" in order to avoid some of this awkwardness.

class GrowableDenseDirichlet(val alpha:Double, p:Seq[Double] = Nil) extends GrowableDenseCountsProportions with MutableDirichlet {
  //def this(alpha:Double) = this(new GrowableUniformProportions(this), new RealVariableParameter(alpha))
  def mean = new GrowableUniformProportions(this)
  def precision = new RealFunction {
    def doubleValue = alpha * GrowableDenseDirichlet.this.length
    def pr = 1.0
    def parents = List(GrowableDenseDirichlet.this) // TODO But note that GrowableDenseDirichlet doesn't have this as a child.
  }
  type CollapsedType = GrowableDenseDirichletMultinomial
  def newCollapsed = new GrowableDenseDirichletMultinomial(alpha)
}

class DenseDirichletMixture(val meanComponents:FiniteMixture[Proportions], 
                            val precisionComponents:FiniteMixture[RealVarParameter],
                            val choice:MixtureChoiceVariable, 
                            initialValue:Seq[Double] = Nil)
extends DenseCountsProportions(meanComponents(choice.intValue)) with MutableDirichlet with MixtureOutcome {
  meanComponents.addChild(this)(null)
  precisionComponents.addChild(this)(null)
  choice.addOutcome(this)
  def mean = meanComponents(choice.intValue)
  def precision = precisionComponents(choice.intValue)
  def logprFromMixtureComponent(index:Int): Double = logprFrom(meanComponents(index), precisionComponents(index))
  def prFromMixtureComponent(index:Int) = Math.exp(logprFromMixtureComponent(index))
  def logprFromMixtureComponent(map:scala.collection.Map[Parameter,Parameter], index:Int): Double = {
    val mean = meanComponents(index)
    val precision = precisionComponents(index)
    logprFrom(map.getOrElse(mean, mean).asInstanceOf[Proportions], map.getOrElse(precision, precision).asInstanceOf[RealVar])
  }
  def prFromMixtureComponent(map:scala.collection.Map[Parameter,Parameter], index:Int) = Math.exp(logprFromMixtureComponent(map, index))
  def parentsFromMixtureComponent(index:Int) = List(meanComponents(index), precisionComponents(index))
  def chosenParents = parentsFromMixtureComponent(choice.intValue)
  override def parents = List[Parameter](meanComponents, precisionComponents) ++ super.parents
  // TODO But note that this below will not yet support sampling of 'choice' with collapsing.
  type CollapsedType = DenseDirichletMultinomial // Make this DenseDirichletMultinomialMixture to support sampling 'choice' with collapsing
  def newCollapsed = {
    //println("DenseDirichletMixture.newCollapsed mean.size="+mean.size)
    new DenseDirichletMultinomial(mean, precision)
  }
}


object DirichletMomentMatching {
  def estimate(mean:DenseProportions, precision:RealVariableParameter, model:Model = cc.factorie.generative.defaultGenerativeModel): Unit = {
    val meanChildren = mean.generatedChildren
    assert(meanChildren.size > 1)
    assert(meanChildren.size == precision.generatedChildren.size) // TODO We are assuming that the contents are the same.
    //val factors = model.factors(List(mean, precision)); assert(factors.size == mean.children.size); assert(factors.size == precision.children.size)
    // Calculcate and set the mean
    val m = new Array[Double](mean.length)
    for (child <- meanChildren) child match { case p:Proportions => forIndex(m.size)(i => m(i) += p(i)) }
    forIndex(m.size)(m(_) /= meanChildren.size)
    mean.set(m)(null)
    // Calculate variance = E[x^2] - E[x]^2 for each dimension
    val variance = new Array[Double](mean.length)
    for (child <- meanChildren) child match { case p:Proportions => forIndex(mean.length)(i => { val diff = p(i) - mean(i); variance(i) += diff * diff })}
    //for (child <- meanChildren) child match { case p:Proportions => forIndex(mean.length)(i => variance(i) += p(i) * p(i)) }
    //println("variance1="+variance.toList)
    forIndex(mean.length)(i => variance(i) /= (meanChildren.size - 1.0))
    //forIndex(mean.length)(i => variance(i) = (variance(i) / meanChildren.size - 1.0) - (m(i) * m(i)))
    //println("variance2="+variance.toList)
    var alphaSum = 0.0
    forIndex(mean.length)(i => if (m(i) != 0.0) alphaSum += math.log((m(i) * (1.0 - m(i)) / variance(i)) - 1.0))
    precision := Math.exp(alphaSum / (mean.length - 1))
    assert(precision.doubleValue == precision.doubleValue, "alphaSum="+alphaSum+" variance="+variance.toList+" mean="+m.toList) // Check for NaN
  }
}