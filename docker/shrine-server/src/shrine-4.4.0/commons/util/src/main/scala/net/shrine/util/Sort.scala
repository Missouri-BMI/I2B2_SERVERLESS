package net.shrine.util

import scala.annotation.tailrec

/**
 * A collection of Comparators/Orderings and sorting functions.
 */
object Sort {
  /**
   * Compare strings based on alpha-numeric order.
   * Based on https://ux.stackexchange.com/questions/95431/how-should-sorting-work-when-numeric-is-mixed-with-alpha-numeric
   *
   * -1 if s1 before s2
   * 0 if equal
   * 1 if s1 after s2
   *
   * Note: Numbers always treated as integers. i.e. 2.0 will be treated as 2, the character '.' then 0
   */
  def compareAlphaNumerically(s1: String, s2: String): Int = {
    // e.g. "text123moretext456" => List("text", "123", "moretext", "456")
    def splitIntoAlphaAndNumeric(s: String): Seq[Either[BigInt, String]] = {
      val nums: Set[Char] = Set('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')

      def takeAllNums(numString: String): (String, String) = numString.span(nums.contains)

      def takeAllNonNums(nonNumString: String): (String, String) = nonNumString.span(!nums.contains(_))

      @tailrec
      def splitIntoParts(str: String, acc: Seq[Either[BigInt, String]]): Seq[Either[BigInt ,String]] = {
        if (str.isEmpty) acc
        else if (nums.contains(str.charAt(0))) {
          val (numPart, rest) = takeAllNums(str)
          splitIntoParts(rest, acc :+ Left(BigInt(numPart)))
        } else {
          val (nonNumPart, rest) = takeAllNonNums(str)
          splitIntoParts(rest, acc :+ Right(nonNumPart))
        }
      }
      splitIntoParts(s, Vector.empty)
    }

    // Go through sections pair-wise and whichever first unequal pair exists, that determines the order of the strings.
    def compareSectionsAlphaNumeric(s1Split: Seq[Either[BigInt, String]], s2Split: Seq[Either[BigInt, String]]): Int = {
      def compareParts(parts: Seq[(Either[BigInt, String], Either[BigInt, String])]): Int = {
        def recur(): Int = compareParts(parts.tail)
        if (parts.isEmpty) s1Split.length - s2Split.length    // The shorter string comes first if all parts match
        else parts.head match {
          case (Left(s1Num: BigInt), Left(s2Num: BigInt)) =>
            if (s1Num != s2Num) s1Num.compareTo(s2Num) // compare them as numbers
            else recur()
          case (Right(s1Str: String), Right(s2Str: String)) =>
            val comp: Int = s1Str.compareTo(s2Str)
            if (comp != 0) comp                     // compare them as strings
            else recur()
          case (Left(s1Num: BigInt), Right(s2Str: String)) => s1Num.toString.compareTo(s2Str)  // compare as strings
          case (Right(s1Str: String), Left(s2Num: BigInt)) => s1Str.compareTo(s2Num.toString)  // compare as string
        }
      }

      compareParts(s1Split.zip(s2Split))
    }

    compareSectionsAlphaNumeric(splitIntoAlphaAndNumeric(s1), splitIntoAlphaAndNumeric(s2))
  }

  def sortAlphaNumerically(list: Seq[String]): Seq[String] = list.sorted(compareAlphaNumerically)

  def orderingFor[T, A](accessor: T => A, comparator: (A, A) => Int): Ordering[T] = (x: T, y: T) => comparator(accessor(x), accessor(y))
}
