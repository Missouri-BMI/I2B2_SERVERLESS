package net.shrine.adapter

import com.typesafe.config.Config
import net.shrine.crypto.SecureRandomSource
import net.shrine.log.Loggable
import net.shrine.protocol.version.v2.ObfuscatingParameters
import net.shrine.protocol.i2b2.{I2b2Result, QueryResult, ResultOutputType}

/**
  * @author Ricardo De Lima
  * @author Bill Simons
  * @author clint
  * @author dwalend
  * @since August 18, 2009
  * @see http://cbmi.med.harvard.edu
 */
case class Obfuscator(obfuscatorParameters:ObfuscatingParameters) extends Loggable {

  //todo a problem instead?
  if((obfuscatorParameters.stdDev < 6.5) ||
    (obfuscatorParameters.noiseClamp < 10) ||
    (obfuscatorParameters.binSize < 5) ||
    (obfuscatorParameters.lowLimit < 10))
    warn(s"""$this does not include enough obfuscation to prevent an unobservable reidentificaiton attack.
         |We recommend stdDev >= 6.5, noiseClamp >= 10, binSize >= 5 , and lowLimit >= 10""".stripMargin)

  def obfuscateResults(doObfuscation: Boolean)(results: Seq[QueryResult]): Seq[QueryResult] = {
    if (doObfuscation) results.map(obfuscate) else results
  }

  def obfuscate(result: QueryResult): QueryResult = {
    val withObfscSetSize = result.modifySetSize(obfuscate)

    if (withObfscSetSize.breakdowns.isEmpty) {
      withObfscSetSize
    }
    else {
      val obfuscatedBreakdowns: Map[ResultOutputType, I2b2Result] = result.breakdowns.view.mapValues(_.mapValues(obfuscate)).toMap

      withObfscSetSize.withBreakdowns(obfuscatedBreakdowns)
    }
  }

//note that the bounds of this obfuscation are actually clamp + binSize/2
  def obfuscate(l: Long): Long = {

    def roundToNearest(i: Double, n: Double): Long = {
      Math.round(
        if ((i % n) >= n / 2) i + n - (i % n) //round up
        else i - i % n //round down
      )
    }

    def clampedGaussian(clamp: Double): Double = {
      val noise = SecureRandomSource.nextGaussian() * obfuscatorParameters.stdDev
      //clamp it
      if (noise > clamp) clamp
      else if (noise < -clamp) -clamp
      else noise
    }

    if((l > obfuscatorParameters.noiseClamp) && (l > obfuscatorParameters.lowLimit)) {
      //bin
      val binned = roundToNearest(l.toDouble, obfuscatorParameters.binSize)

      //add noise
      val noised = binned + clampedGaussian(obfuscatorParameters.noiseClamp)

      //bin again
      val rounded = roundToNearest(noised, obfuscatorParameters.binSize)

      //finally, if rounded is low limit or smaller, report that the result is too small.
      if ((rounded > obfuscatorParameters.noiseClamp) || (rounded > obfuscatorParameters.lowLimit)) rounded
      else Obfuscator.LESS_THAN_LOW_LIMIT //will be reported as "$clamped or fewer"
    }
    else Obfuscator.LESS_THAN_LOW_LIMIT
  }
}

object Obfuscator {
  def apply(config:Config): Obfuscator = Obfuscator(ObfuscatingParameters.fromConfig(config))

  val LESS_THAN_LOW_LIMIT: Long = -1L
}