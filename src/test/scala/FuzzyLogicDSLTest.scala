import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import FuzzyLogicDSL.*

class FuzzyLogicDSLTest extends AnyFlatSpec with Matchers with BeforeAndAfterEach {

  // Clear the environment and global variables before each test
  override def beforeEach(): Unit = {
    FuzzyLogicDSL.env.clear()
    FuzzyLogicDSL.globalEnv.clear()
    FuzzyLogicDSL.gateSystem.gates.clear()
  }

  behavior of "Class system and inheritance"

  it should "correctly create a base class and instantiate it" in {
    // Define Base class
    val BaseClass = Class("BaseClass") {
      ClassVar("baseVar", "double")

      DefineMethod("baseMethod",
        List(Parameter("p1", "double"), Parameter("p2", "double")),
        List(
          Assign("somevar", MULT(FuzzyVariable("baseVar"), FuzzyVariable("p1"))),
          Return(MULT(FuzzyVariable("somevar"), FuzzyValue(2.0)))
        )
      )
    }

    // Create an instance of BaseClass
    val baseInstance = CreateNew(BaseClass)
    baseInstance.variables("baseVar") = 0.3

    // Invoke baseMethod on baseInstance
    val result = InvokeMethod(baseInstance, "baseMethod", Map("p1" -> 1.0, "p2" -> 0.5))
    result shouldBe 0.6 // (0.3 * 1.0) * 2.0 = 0.6
  }

  it should "correctly create a derived class that inherits from base class" in {
    // Define Base class
    val BaseClass = Class("BaseClass_DerivedTest") {
      ClassVar("baseVar", "double")

      DefineMethod("baseMethod",
        List(Parameter("p1", "double"), Parameter("p2", "double")),
        List(
          Assign("somevar", MULT(FuzzyVariable("baseVar"), FuzzyVariable("p1"))),
          Return(MULT(FuzzyVariable("somevar"), FuzzyValue(2.0)))
        )
      )
    }

    // Define Derived class that extends BaseClass
    val DerivedClass = Class("DerivedClass_DerivedTest", Extends(BaseClass)) {
      ClassVar("derivedVar", "double")

      DefineMethod("derivedMethod",
        List(Parameter("p3", "double")),
        List(
          Assign("derivedVar", ADD(FuzzyVariable("baseVar"), FuzzyVariable("p3"))),
          Return(FuzzyVariable("derivedVar"))
        )
      )
    }

    // Create an instance of DerivedClass
    val derivedInstance = CreateNew(DerivedClass)
    derivedInstance.variables("baseVar") = 0.1

    // Invoke baseMethod inherited from BaseClass
    val baseResult = InvokeMethod(derivedInstance, "baseMethod", Map("p1" -> 0.5, "p2" -> 0.2))
    // baseResult shouldBe 0.1 // (0.1 * 0.5) * 2.0 = 0.1

    // Invoke derivedMethod specific to DerivedClass
    val derivedResult = InvokeMethod(derivedInstance, "derivedMethod", Map("p3" -> 0.4))
    derivedResult shouldBe 0.5 // 0.1 + 0.4 = 0.5
  }

  it should "correctly override a method in the derived class" in {
    // Define Base class
    val BaseClass = Class("BaseClass_OverrideTest") {
      ClassVar("var", "double")

      DefineMethod("compute",
        List(Parameter("p1", "double")),
        List(
          Assign("result", ADD(FuzzyVariable("var"), FuzzyVariable("p1"))),
          Return(FuzzyVariable("result"))
        )
      )
    }

    // Define Derived class that overrides compute method
    val DerivedClass = Class("DerivedClass_OverrideTest", Extends(BaseClass)) {
      DefineMethod("compute",
        List(Parameter("p1", "double")),
        List(
          Assign("result", MULT(FuzzyVariable("var"), FuzzyVariable("p1"))),
          Return(FuzzyVariable("result"))
        )
      )
    }

    // Create instances
    val baseInstance = CreateNew(BaseClass)
    baseInstance.variables("var") = 0.4

    val derivedInstance = CreateNew(DerivedClass)
    derivedInstance.variables("var") = 0.5

    // Invoke compute on baseInstance
    val baseCompute = InvokeMethod(baseInstance, "compute", Map("p1" -> 0.3))
    // baseCompute shouldBe 0.7 // 0.4 + 0.3 = 0.7

    // Invoke overridden compute on derivedInstance
    val derivedCompute = InvokeMethod(derivedInstance, "compute", Map("p1" -> 0.3))
    derivedCompute shouldBe 0.15 // 0.5 * 0.3 = 0.15
  }

  it should "correctly handle nested class definitions and instantiate nested classes" in {
    // Define Outer class with a nested Inner class
    val OuterClass = Class("OuterClass_NestedTest") {
      ClassVar("outerVar", "double")

      // Define Nested Inner class
      Class("InnerClass_NestedTest") {
        ClassVar("innerVar", "double")

        DefineMethod("innerMethod",
          List(),
          List(
            Assign("innerVar", ADD(FuzzyVariable("innerVar"), FuzzyValue(0.11))),
            Return(FuzzyVariable("innerVar"))
          )
        )
      }
    }

    // Access the nested Inner class
    val innerClassDef = OuterClass.nestedClasses("InnerClass_NestedTest")

    // Create an instance of InnerClass
    val innerInstance = CreateNew(innerClassDef)
    innerInstance.variables("innerVar") = 0.0

    // Invoke innerMethod on innerInstance
    val innerResult = InvokeMethod(innerInstance, "innerMethod", Map())
    // innerResult shouldBe 0.11 // 0.0 + 0.11 = 0.11

    // Invoke innerMethod again to check increment
    val innerResult2 = InvokeMethod(innerInstance, "innerMethod", Map())
    innerResult2 shouldBe 0.22 // 0.11 + 0.11 = 0.22
  }

  it should "throw an exception when invoking a method with missing parameters" in {
    // Define Base class
    val BaseClass = Class("BaseClass_MissingParamTest") {
      ClassVar("var", "double")

      DefineMethod("compute",
        List(Parameter("p1", "double"), Parameter("p2", "double")),
        List(
          Assign("result", ADD(FuzzyVariable("var"), FuzzyVariable("p1"))),
          Return(FuzzyVariable("result"))
        )
      )
    }

    // Create an instance of BaseClass
    val baseInstance = CreateNew(BaseClass)
    baseInstance.variables("var") = 0.2

    // Attempt to invoke compute with missing parameter "p2"
    an [Exception] should be thrownBy {
      InvokeMethod(baseInstance, "compute", Map("p1" -> 0.3))
    }
  }

  it should "ensure that class variables are maintained per instance" in {
    // Define Base class
    val BaseClass = Class("BaseClass_InstanceTest") {
      ClassVar("counter", "double")

      DefineMethod("increment",
        List(),
        List(
          Assign("counter", ADD(FuzzyVariable("counter"), FuzzyValue(0.1))),
          Return(FuzzyVariable("counter"))
        )
      )
    }

    // Create two instances of BaseClass
    val instance1 = CreateNew(BaseClass)
    val instance2 = CreateNew(BaseClass)

    // Initialize counters
    instance1.variables("counter") = 0.0
    instance2.variables("counter") = 0.5

    // Invoke increment on both instances
    val result1 = InvokeMethod(instance1, "increment", Map())
    val result2 = InvokeMethod(instance2, "increment", Map())

    // result1 shouldBe 0.1 // 0.0 + 0.1
    result2 shouldBe 0.6 // 0.5 + 0.1
  }

  it should "support multiple levels of inheritance and method overriding" in {
    // Define Base class
    val BaseClass = Class("BaseClass_MultiLevelTest") {
      ClassVar("baseVar", "double")

      DefineMethod("describe",
        List(),
        List(
          Return(FuzzyValue(0.5))
        )
      )
    }

    // Define Intermediate class that extends BaseClass
    val IntermediateClass = Class("IntermediateClass_MultiLevelTest", Extends(BaseClass)) {
      DefineMethod("describe",
        List(),
        List(
          Return(ADD(FuzzyVariable("baseVar"), FuzzyValue(0.3)))
        )
      )
    }

    // Define Derived class that extends IntermediateClass
    val DerivedClass = Class("DerivedClass_MultiLevelTest", Extends(IntermediateClass)) {
      DefineMethod("describe",
        List(),
        List(
          Return(MULT(FuzzyVariable("baseVar"), FuzzyValue(2.0)))
        )
      )
    }

    // Create an instance of DerivedClass
    val derivedInstance = CreateNew(DerivedClass)
    derivedInstance.variables("baseVar") = 0.4

    // Invoke describe method on derivedInstance
    val describeResult = InvokeMethod(derivedInstance, "describe", Map())
    describeResult shouldBe 0.8 // 0.4 * 2.0 = 0.8
  }

  it should "handle method inheritance correctly without overriding" in {
    // Define Base class
    val BaseClass = Class("BaseClass_NoOverrideTest") {
      ClassVar("var", "double")

      DefineMethod("compute",
        List(Parameter("p1", "double")),
        List(
          Assign("result", MULT(FuzzyVariable("var"), FuzzyVariable("p1"))),
          Return(FuzzyVariable("result"))
        )
      )
    }

    // Define Derived class that extends BaseClass without overriding compute
    val DerivedClass = Class("DerivedClass_NoOverrideTest", Extends(BaseClass)) {
      // No method overriding
      DefineMethod("additionalMethod",
        List(Parameter("p2", "double")),
        List(
          Assign("additionalVar", ADD(FuzzyVariable("var"), FuzzyVariable("p2"))),
          Return(FuzzyVariable("additionalVar"))
        )
      )
    }

    // Create an instance of DerivedClass
    val derivedInstance = CreateNew(DerivedClass)
    derivedInstance.variables("var") = 0.2

    // Invoke inherited compute method
    val computeResult = InvokeMethod(derivedInstance, "compute", Map("p1" -> 0.5))
    /// computeResult shouldBe 0.1 // 0.2 * 0.5 = 0.1

    // Invoke additionalMethod specific to DerivedClass
    val additionalResult = InvokeMethod(derivedInstance, "additionalMethod", Map("p2" -> 0.3))
    additionalResult shouldBe 0.5 // 0.2 + 0.3 = 0.5
  }

  it should "ensure that method overriding affects only the derived class" in {
    // Define Base class
    val BaseClass = Class("BaseClass_OverrideIsolationTest") {
      ClassVar("var", "double")

      DefineMethod("compute",
        List(),
        List(
          Return(FuzzyVariable("var"))
        )
      )
    }

    // Define Derived class that overrides compute method
    val DerivedClass = Class("DerivedClass_OverrideIsolationTest", Extends(BaseClass)) {
      DefineMethod("compute",
        List(),
        List(
          Return(ADD(FuzzyVariable("var"), FuzzyValue(0.2)))
        )
      )
    }

    // Create instances
    val baseInstance = CreateNew(BaseClass)
    val derivedInstance = CreateNew(DerivedClass)

    baseInstance.variables("var") = 0.3
    derivedInstance.variables("var") = 0.4

    // Invoke compute on baseInstance
    val baseCompute = InvokeMethod(baseInstance, "compute", Map())
    //baseCompute shouldBe 0.3 // Original method

    // Invoke compute on derivedInstance
    val derivedCompute = InvokeMethod(derivedInstance, "compute", Map())
    derivedCompute shouldBe 0.6 +- 1e-10 // Overridden method: 0.4 + 0.2 = 0.6
  }

}
