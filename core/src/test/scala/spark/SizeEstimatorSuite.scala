package spark

import org.scalatest.FunSuite
import org.scalatest.BeforeAndAfterAll
import org.scalatest.PrivateMethodTester

class DummyClass1 {}

class DummyClass2 {
  val x: Int = 0
}

class DummyClass3 {
  val x: Int = 0
  val y: Double = 0.0
}

class DummyClass4(val d: DummyClass3) {
  val x: Int = 0
}

object DummyString {
  def apply(str: String) : DummyString = new DummyString(str.toArray)
}
class DummyString(val arr: Array[Char]) {
  override val hashCode: Int = 0
  // JDK-7 has an extra hash32 field http://hg.openjdk.java.net/jdk7u/jdk7u6/jdk/rev/11987e85555f
  @transient val hash32: Int = 0
}

class SizeEstimatorSuite
  extends FunSuite with BeforeAndAfterAll with PrivateMethodTester {

  var oldArch: String = _
  var oldOops: String = _

  override def beforeAll() {
    // Set the arch to 64-bit and compressedOops to true to get a deterministic test-case
    oldArch = System.setProperty("os.arch", "amd64")
    oldOops = System.setProperty("spark.test.useCompressedOops", "true")
  }

  override def afterAll() {
    resetOrClear("os.arch", oldArch)
    resetOrClear("spark.test.useCompressedOops", oldOops)
  }

  test("simple classes") {
    assert(SizeEstimator.estimate(new DummyClass1) === 16)
    assert(SizeEstimator.estimate(new DummyClass2) === 16)
    assert(SizeEstimator.estimate(new DummyClass3) === 24)
    assert(SizeEstimator.estimate(new DummyClass4(null)) === 24)
    assert(SizeEstimator.estimate(new DummyClass4(new DummyClass3)) === 48)
  }

  // NOTE: The String class definition varies across JDK versions (1.6 vs. 1.7) and vendors
  // (Sun vs IBM). Use a DummyString class to make tests deterministic.
  test("strings") {
    assert(SizeEstimator.estimate(DummyString("")) === 40)
    assert(SizeEstimator.estimate(DummyString("a")) === 48)
    assert(SizeEstimator.estimate(DummyString("ab")) === 48)
    assert(SizeEstimator.estimate(DummyString("abcdefgh")) === 56)
  }

  test("primitive arrays") {
    assert(SizeEstimator.estimate(new Array[Byte](10)) === 32)
    assert(SizeEstimator.estimate(new Array[Char](10)) === 40)
    assert(SizeEstimator.estimate(new Array[Short](10)) === 40)
    assert(SizeEstimator.estimate(new Array[Int](10)) === 56)
    assert(SizeEstimator.estimate(new Array[Long](10)) === 96)
    assert(SizeEstimator.estimate(new Array[Float](10)) === 56)
    assert(SizeEstimator.estimate(new Array[Double](10)) === 96)
    assert(SizeEstimator.estimate(new Array[Int](1000)) === 4016)
    assert(SizeEstimator.estimate(new Array[Long](1000)) === 8016)
  }

  test("object arrays") {
    // Arrays containing nulls should just have one pointer per element
    assert(SizeEstimator.estimate(new Array[String](10)) === 56)
    assert(SizeEstimator.estimate(new Array[AnyRef](10)) === 56)

    // For object arrays with non-null elements, each object should take one pointer plus
    // however many bytes that class takes. (Note that Array.fill calls the code in its
    // second parameter separately for each object, so we get distinct objects.)
    assert(SizeEstimator.estimate(Array.fill(10)(new DummyClass1)) === 216)
    assert(SizeEstimator.estimate(Array.fill(10)(new DummyClass2)) === 216)
    assert(SizeEstimator.estimate(Array.fill(10)(new DummyClass3)) === 296)
    assert(SizeEstimator.estimate(Array(new DummyClass1, new DummyClass2)) === 56)

    // Past size 100, our samples 100 elements, but we should still get the right size.
    assert(SizeEstimator.estimate(Array.fill(1000)(new DummyClass3)) === 28016)

    // If an array contains the *same* element many times, we should only count it once.
    val d1 = new DummyClass1
    assert(SizeEstimator.estimate(Array.fill(10)(d1)) === 72) // 10 pointers plus 8-byte object
    assert(SizeEstimator.estimate(Array.fill(100)(d1)) === 432) // 100 pointers plus 8-byte object

    // Same thing with huge array containing the same element many times. Note that this won't
    // return exactly 4032 because it can't tell that *all* the elements will equal the first
    // one it samples, but it should be close to that.

    // TODO: If we sample 100 elements, this should always be 4176 ?
    val estimatedSize = SizeEstimator.estimate(Array.fill(1000)(d1))
    assert(estimatedSize >= 4000, "Estimated size " + estimatedSize + " should be more than 4000")
    assert(estimatedSize <= 4200, "Estimated size " + estimatedSize + " should be less than 4100")
  }

  test("32-bit arch") {
    val arch = System.setProperty("os.arch", "x86")

    val initialize = PrivateMethod[Unit]('initialize)
    SizeEstimator invokePrivate initialize()

    assert(SizeEstimator.estimate(DummyString("")) === 40)
    assert(SizeEstimator.estimate(DummyString("a")) === 48)
    assert(SizeEstimator.estimate(DummyString("ab")) === 48)
    assert(SizeEstimator.estimate(DummyString("abcdefgh")) === 56)

    resetOrClear("os.arch", arch)
  }

  // NOTE: The String class definition varies across JDK versions (1.6 vs. 1.7) and vendors
  // (Sun vs IBM). Use a DummyString class to make tests deterministic.
  test("64-bit arch with no compressed oops") {
    val arch = System.setProperty("os.arch", "amd64")
    val oops = System.setProperty("spark.test.useCompressedOops", "false")

    val initialize = PrivateMethod[Unit]('initialize)
    SizeEstimator invokePrivate initialize()

    assert(SizeEstimator.estimate(DummyString("")) === 56)
    assert(SizeEstimator.estimate(DummyString("a")) === 64)
    assert(SizeEstimator.estimate(DummyString("ab")) === 64)
    assert(SizeEstimator.estimate(DummyString("abcdefgh")) === 72)

    resetOrClear("os.arch", arch)
    resetOrClear("spark.test.useCompressedOops", oops)
  }

  def resetOrClear(prop: String, oldValue: String) {
    if (oldValue != null) {
      System.setProperty(prop, oldValue)
    } else {
      System.clearProperty(prop)
    }
  }
}
