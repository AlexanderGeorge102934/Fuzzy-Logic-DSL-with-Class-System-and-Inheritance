import scala.collection.mutable

type Environment = mutable.Map[String, mutable.Map[String, Double]] // Map from gate name to variables
type GlobalEnvironment = mutable.Map[String, Double] // Global variable environment

object FuzzyLogicDSL:

  given env: Environment = mutable.Map() // Environment for storing variables within specific gates
  private val globalEnv: GlobalEnvironment = mutable.Map() // Global environment for variables not tied to a specific gate

  // Abstract class for fuzzy expressions, with the 'eval' method for evaluation
  abstract class FuzzyExpr:
    def eval(localEnv: mutable.Map[String, Double] = mutable.Map(), gateName: String = ""): Double

  // Classes for the class system
  case class ClassDef(
                       name: String,
                       variables: mutable.Map[String, Double] = mutable.Map(),
                       methods: mutable.Map[String, MethodDef] = mutable.Map(),
                       parent: Option[ClassDef] = None,
                       nestedClasses: mutable.Map[String, ClassDef] = mutable.Map()
                     )

  case class MethodDef(name: String, parameters: List[Parameter], body: List[Statement])

  case class Parameter(name: String, paramType: String)

  abstract class Statement:
    def execute(localEnv: mutable.Map[String, Double]): Unit

  case class Assignment(variable: String, expr: FuzzyExpr) extends Statement:
    def execute(localEnv: mutable.Map[String, Double]): Unit =
      localEnv(variable) = expr.eval(localEnv)

  case class ReturnStatement(expr: FuzzyExpr) extends Statement:
    def execute(localEnv: mutable.Map[String, Double]): Unit =
      throw ReturnException(expr.eval(localEnv))

  case class ExpressionStatement(expr: FuzzyExpr) extends Statement:
    def execute(localEnv: mutable.Map[String, Double]): Unit =
      expr.eval(localEnv) // Evaluate the expression, but ignore result

  private case class ReturnException(value: Double) extends Exception

  case class Instance(classDef: ClassDef, variables: mutable.Map[String, Double] = mutable.Map())

  // The class registry holds all class definitions
  private val classRegistry: mutable.Map[String, ClassDef] = mutable.Map()

  // Stack to keep track of the current class context
  private val currentClassStack: mutable.Stack[ClassDef] = mutable.Stack()

  // Function to define a class
  def Class(name: String, parent: Option[ClassDef] = None)(body: => Unit = ()): ClassDef =
    val classDef = ClassDef(name, parent = parent)
    if currentClassStack.nonEmpty then
      val currentClass = currentClassStack.top
      currentClass.nestedClasses(name) = classDef
    else
      classRegistry(name) = classDef
    currentClassStack.push(classDef)
    try
      body
    finally
      currentClassStack.pop()
    classDef

  // Function to specify inheritance
  def Extends(parentClass: ClassDef): Option[ClassDef] = Some(parentClass)

  // Function to define a class variable
  def ClassVar(name: String, varType: String): Unit =
    val currentClass = currentClassStack.top
    if varType != "double" then
      throw new Exception(s"Only 'double' type is supported, but got '$varType'")
    currentClass.variables(name) = 0.0 // Initialize to 0.0

  // Function to define a method
  def DefineMethod(name: String, parameters: List[Parameter], body: List[Statement]): Unit =
    val currentClass = currentClassStack.top
    currentClass.methods(name) = MethodDef(name, parameters, body)

  // Function to create a new instance of a class
  def CreateNew(classDef: ClassDef): Instance =
    val instanceVariables = mutable.Map[String, Double]()
    // Initialize variables, including inherited variables
    def collectVariables(c: ClassDef): Unit =
      if c.parent.isDefined then collectVariables(c.parent.get)
      c.variables.foreach { case (name, value) =>
        if !instanceVariables.contains(name) then
          instanceVariables(name) = value
      }
    collectVariables(classDef)
    Instance(classDef, instanceVariables)

  // Function to invoke a method on an instance
  def InvokeMethod(instance: Instance, methodName: String, args: Map[String, Double]): Double =
    // Find method in class hierarchy
    def findMethod(c: ClassDef): Option[MethodDef] =
      c.methods.get(methodName) match
        case someMethod@Some(_) => someMethod
        case None => c.parent.flatMap(findMethod)
    findMethod(instance.classDef) match
      case Some(methodDef) =>
        // Create a local environment for method execution
        val localVariables = mutable.Map[String, Double]()
        // Add instance variables
        localVariables ++= instance.variables
        // Add parameters
        methodDef.parameters.foreach { param =>
          if args.contains(param.name) then
            localVariables(param.name) = args(param.name)
          else
            throw new Exception(s"Missing argument for parameter ${param.name}")
        }
        // Execute the method body
        try
          methodDef.body.foreach(_.execute(localVariables))
          // If no return statement, return 0.0
          0.0
        catch
          case ReturnException(value) => value
        finally
          // Update instance variables with all variables from localVariables
          instance.variables ++= localVariables
      case None => throw new Exception(s"Method $methodName not found in class hierarchy of ${instance.classDef.name}")

  // Functions to create statements
  def Assign(variable: String, expr: FuzzyExpr): Assignment = Assignment(variable, expr)
  def Return(expr: FuzzyExpr): ReturnStatement = ReturnStatement(expr)
  def Expr(expr: FuzzyExpr): ExpressionStatement = ExpressionStatement(expr)

  // Fuzzy Expressions
  case class FuzzyValue(v: Double) extends FuzzyExpr:
    def eval(localEnv: mutable.Map[String, Double] = mutable.Map(), gateName: String = ""): Double = v

  case class FuzzyVariable(name: String) extends FuzzyExpr:
    def eval(localEnv: mutable.Map[String, Double] = mutable.Map(), gateName: String = ""): Double =
      localEnv.getOrElse(name,
        // Check the gate's environment if gateName is provided
        if gateName != "" && summon[Environment].contains(gateName) then
          summon[Environment](gateName).getOrElse(name,
            globalEnv.getOrElse(name, throw new Exception(s"Variable $name not found")))
        else
          globalEnv.getOrElse(name, throw new Exception(s"Variable $name not found"))
      )

  // Fuzzy Operations
  private case class FuzzyAdd(e1: FuzzyExpr, e2: FuzzyExpr) extends FuzzyExpr:
    def eval(localEnv: mutable.Map[String, Double] = mutable.Map(), gateName: String = ""): Double =
      math.min(1.0, e1.eval(localEnv, gateName) + e2.eval(localEnv, gateName))

  private case class FuzzyMult(e1: FuzzyExpr, e2: FuzzyExpr) extends FuzzyExpr:
    def eval(localEnv: mutable.Map[String, Double] = mutable.Map(), gateName: String = ""): Double =
      e1.eval(localEnv, gateName) * e2.eval(localEnv, gateName)

  private case class FuzzyComplement(e: FuzzyExpr) extends FuzzyExpr:
    def eval(localEnv: mutable.Map[String, Double] = mutable.Map(), gateName: String = ""): Double =
      1.0 - e.eval(localEnv, gateName)

  private case class FuzzyAND(e1: FuzzyExpr, e2: FuzzyExpr) extends FuzzyExpr:
    def eval(localEnv: mutable.Map[String, Double] = mutable.Map(), gateName: String = ""): Double =
      math.min(e1.eval(localEnv, gateName), e2.eval(localEnv, gateName)) // AND is min

  private case class FuzzyOR(e1: FuzzyExpr, e2: FuzzyExpr) extends FuzzyExpr:
    def eval(localEnv: mutable.Map[String, Double] = mutable.Map(), gateName: String = ""): Double =
      math.max(e1.eval(localEnv, gateName), e2.eval(localEnv, gateName)) // OR is max

  private case class FuzzyXOR(e1: FuzzyExpr, e2: FuzzyExpr) extends FuzzyExpr:
    def eval(localEnv: mutable.Map[String, Double] = mutable.Map(), gateName: String = ""): Double =
      math.abs(e1.eval(localEnv, gateName) - e2.eval(localEnv, gateName)) // XOR formula

  private case class FuzzyAlphaCut(e: FuzzyExpr, alpha: Double) extends FuzzyExpr:
    def eval(localEnv: mutable.Map[String, Double] = mutable.Map(), gateName: String = ""): Double =
      if e.eval(localEnv, gateName) >= alpha then e.eval(localEnv, gateName) else 0.0 // Alpha Cut

  // Assign variable to local/global scope or gate to expression
  def Assign(left: Any, right: FuzzyExpr)(using gate: Gate = Gate("global")): Unit = left match
    case FuzzyVariable(name) =>
      if gate.name == "global" then // If global assign global scope
        globalEnv.update(name, right.eval())
      else
        summon[Environment].getOrElseUpdate(gate.name, mutable.Map[String, Double]()).update(name, right.eval())
    // Assign a fuzzy expression to a Gate (store it in the gate system)
    case gate: Gate => gateSystem.gates(gate.name) = right
    case _ => throw new Exception("Invalid assignment")

  // Define a scope for a specific gate, allowing assignments and operations within the gate
  def Scope(gate: Gate)(block: Gate ?=> Unit): Unit =
    given Gate = gate
    block

  // Define an anonymous scope that temporarily modifies variables and restores them after execution
  def AnonymousScope(block: => Unit): Unit =
    // Save current environment specific gates
    val originalEnv = summon[Environment].map { case (k, v) => k -> v.clone() }
    val originalGlobalEnv = globalEnv.clone()
    try
      block
    finally
      // Restore environments and globals
      summon[Environment].clear()
      summon[Environment].addAll(originalEnv)
      globalEnv.clear()
      globalEnv.addAll(originalGlobalEnv)

  // Test the logic gate result using the active bindings in the gate's context or globally
  def TestGate(gateName: String, variableName: String): Double =
    // Check if a gate exists and the variable is scoped within that gate
    if summon[Environment].contains(gateName) then
      summon[Environment](gateName).get(variableName) match
        case Some(value) => value
        // Look in global scope if not in local
        case None => globalEnv.getOrElse(variableName, throw new Exception(s"Variable $variableName not found globally or in gate $gateName"))
    else
      // If no gate look at global
      globalEnv.getOrElse(variableName, throw new Exception(s"Variable $variableName not found globally or in gate $gateName"))

  // Evaluate the expression assigned to the gate, if any
  def EvaluateGateExpression(gateName: String): Double =
    gateSystem.gates.get(gateName) match
      case Some(expr) =>
        val localEnv = summon[Environment].getOrElse(gateName, mutable.Map())
        expr.eval(localEnv, gateName)
      case None => throw new Exception(s"No expression assigned to gate $gateName")

  // Functions for fuzzy operations
  def ADD(e1: FuzzyExpr, e2: FuzzyExpr): FuzzyExpr = FuzzyAdd(e1, e2)
  def MULT(e1: FuzzyExpr, e2: FuzzyExpr): FuzzyExpr = FuzzyMult(e1, e2)
  def COMPLEMENT(e: FuzzyExpr): FuzzyExpr = FuzzyComplement(e)
  def AND(e1: FuzzyExpr, e2: FuzzyExpr): FuzzyExpr = FuzzyAND(e1, e2)
  def OR(e1: FuzzyExpr, e2: FuzzyExpr): FuzzyExpr = FuzzyOR(e1, e2)
  def XOR(e1: FuzzyExpr, e2: FuzzyExpr): FuzzyExpr = FuzzyXOR(e1, e2)
  def ALPHA_CUT(e: FuzzyExpr, alpha: Double): FuzzyExpr = FuzzyAlphaCut(e, alpha)

  // Gate and GateSystem definitions remain unchanged
  case class Gate(name: String) // Define a gate by name
  private case class GateSystem(gates: mutable.Map[String, FuzzyExpr] = mutable.Map()) // Holds all gates and their associated fuzzy expressions
  private val gateSystem = GateSystem()

object Main:
  import FuzzyLogicDSL.*

  def main(args: Array[String]): Unit =
    println("Running Class and Inheritance Tests")

    // Define Base class
    val BaseClass = Class("Base") {
      // Define class variables
      ClassVar("var", "double")

      // Define method m1
      DefineMethod("m1",
        List(Parameter("p1", "double"), Parameter("p2", "double")),
        List(
          Assign("somevar", MULT(FuzzyVariable("var"), FuzzyVariable("p1"))),
          Return(MULT(FuzzyVariable("somevar"), FuzzyValue(2.0)))
        )
      )
    }

    val baseClassInstance = CreateNew(BaseClass)

    baseClassInstance.variables("var") = 0.3

    val resultBaseClass = InvokeMethod(baseClassInstance, "m1", Map("p1" -> 1.0, "p2" -> 0.50))
    println(s"Result of invoking m1 on Base instance (Should output 0.6): $resultBaseClass") // Should output 0.6

    // Define Derived class that extends Base
    val DerivedClass = Class("Derived", Extends(BaseClass)) {
      // Additional class variables or methods can be added here
      ClassVar("derivedVar", "double")

      ClassVar("example", "double")
      //      Assign("example", FuzzyValue(0.8)) // Class var values can be made when instantiated or in a method but not in the class definition (Default is 0)



      // Define a new method in Derived
      DefineMethod("m2",
        List(Parameter("p3", "double")),
        List(
          Assign("derivedVar", ADD(FuzzyVariable("var"), FuzzyVariable("p3"))),
          Return(FuzzyVariable("derivedVar"))
        )
      )

      // Override method m1
      DefineMethod("m1",
        List(Parameter("p1", "double"), Parameter("p2", "double")),
        List(
          Assign("somevar", ADD(FuzzyVariable("var"), FuzzyVariable("p1"))),
          Return(ADD(FuzzyVariable("somevar"), FuzzyValue(0.0)))
        )
      )

      DefineMethod("emptyMethod",
        List(),
        List(

          Return(FuzzyValue(0.0))
        )
      )
    }

    // Create an instance of Derived
    val derivedInstance = CreateNew(DerivedClass)

    // Initialize instance variable 'var'
    derivedInstance.variables("var") = 0.1

    // Invoking on empty method with no parameters
    val emptyDerivedM1 = InvokeMethod(derivedInstance, "emptyMethod", Map())
    println(s"Result of invoking empty Method on Derived instance (overridden) (Should output 0): $emptyDerivedM1")

    // Invoke method m1 on the derived instance (should use overridden method)
    val resultDerivedM1 = InvokeMethod(derivedInstance, "m1", Map("p1" -> 0.12, "p2" -> 0.33))
    println(s"Result of invoking m1 on Derived instance (overridden) (Should output 0.22): $resultDerivedM1") // Should output 0.22

    // Verify that the instance variable 'somevar' has been updated
    println(s"Value of 'somevar' in derived instance after method m1 call (Should output 0.22): ${derivedInstance.variables.getOrElse("somevar", "Not found")}") // Should output 0.22

    println(s"Value of 'example' in derived instance after method m1 call (Should output 0.8): ${derivedInstance.variables.getOrElse("example", "Not found")}")
    // Invoke new method m2 on the derived instance
    val resultDerivedM2 = InvokeMethod(derivedInstance, "m2", Map("p3" -> 0.70))
    println(s"Result of invoking m2 on Derived instance (Should output 0.8): $resultDerivedM2") // Should output 0.8

    // Verify that 'derivedVar' has been updated
    println(s"Value of 'derivedVar' in derived instance after method m2 call (Should output 0.8): ${derivedInstance.variables.getOrElse("derivedVar", "Not found")}")

    // Attempt to invoke m2 on Base instance (should fail)
    try
      val resultBaseM2 = InvokeMethod(baseClassInstance, "m2", Map("p3" -> 4.0))
      println(s"Result of invoking m2 on Base instance: $resultBaseM2")
    catch
      case e: Exception =>
        println(s"Error invoking m2 on Base instance: ${e.getMessage}")

    // Test nested classes
    val OuterClass = Class("Outer") {
      ClassVar("outerVar", "double")
      // Nested class
      Class("Inner") {
        ClassVar("innerVar", "double")
        DefineMethod("innerMethod", List(), List(
          Assign("innerVar", FuzzyValue(11.0)),
          Return(FuzzyVariable("innerVar"))
        ))
      }
    }

    // Access the nested class
    val InnerClass = OuterClass.nestedClasses("Inner")

    // Create an instance of the nested class
    val innerInstance = CreateNew(InnerClass)

    // Invoke method on the nested class instance
    val innerResult = InvokeMethod(innerInstance, "innerMethod", Map())
    println(s"Result of invoking innerMethod on Inner instance: $innerResult") // Should output 11.0

    // Additional test: Accessing outer class variable from inner class (if desired)

    innerInstance.variables("outerVar") = 0.1
    println(s"Result of accessing outer variable from inner class (Should be 0.1): ${innerInstance.variables("outerVar")}") // Should output 11.0
    // Since our current implementation doesn't support accessing outer class variables directly, we can extend it or note the limitation

    // Ensure previous functionality still works
    println("\nRunning Scope and Assignment Tests")

    // Assign global variables X and Y
    Assign(FuzzyVariable("X"), FuzzyValue(0.2)) // Global assignment
    Assign(FuzzyVariable("Y"), FuzzyValue(0.4)) // Global assignment

    // Test global assignments
    println(s"Global X = ${TestGate("global", "X")}") // Should return 0.2
    println(s"Global Y = ${TestGate("global", "Y")}") // Should return 0.4

    // Assign variables A and B within a scope (logicGate1)
    Scope(Gate("logicGate1")) {
      Assign(FuzzyVariable("A"), FuzzyValue(0.5))
      Assign(FuzzyVariable("B"), FuzzyValue(0.7))
    }

    // Assign an expression to a gate (logicGate1) and evaluate it
    Assign(Gate("logicGate1"), ADD(FuzzyVariable("A"), FuzzyVariable("B")))(using Gate("logicGate1"))

    // Test the results of logicGate1
    println(s"TestGate result for A in logicGate1 = ${TestGate("logicGate1", "A")}") // Should return 0.5
    println(s"TestGate result for Global X in logicGate1 = ${TestGate("logicGate1", "X")}") // Should return 0.2
    println(s"Expression result for logicGate1 A + B = ${EvaluateGateExpression("logicGate1")}") // Should evaluate A + B = 0.5 + 0.7

    // Change the value of X locally in logicGate1
    Scope(Gate("logicGate1")) {
      Assign(FuzzyVariable("X"), FuzzyValue(0.7))
    }

    // Test the local and global values of X
    println(s"TestGate result for local X in logicGate1 = ${TestGate("logicGate1", "X")}") // Should return 0.7
    println(s"TestGate result for Global X after locally changing local X = ${TestGate("global", "X")}") // Should return 0.2

    // Using an anonymous scope to temporarily change values
    AnonymousScope {
      Assign(FuzzyVariable("Y"), FuzzyValue(0.90)) // Global assignment within anon scope
      println(s"Global Y in Anon Scope = ${TestGate("global", "Y")}") // Should return 0.9

      // Assign a temporary variable in tempGate
      Scope(Gate("tempGate")) {
        Assign(FuzzyVariable("tempVar"), FuzzyValue(0.11)) // Assign a value to tempVar
      }

      // Temporarily modify A in logicGate1 within anon scope
      Scope(Gate("logicGate1")) {
        Assign(FuzzyVariable("A"), FuzzyValue(0.29))
      }

      // Test logicGate1 with modified A
      println(s"Expression result for logicGate1 A + B (A is now 0.29, Anon Scope) = ${EvaluateGateExpression("logicGate1")}") // Should evaluate A + B = 0.29 + 0.7

      // Evaluate an expression in tempGate
      Assign(Gate("tempGate"), ADD(MULT(FuzzyValue(0.9), FuzzyValue(0.2)), FuzzyValue(0.3)))(using Gate("tempGate"))
      println(s"Expression result for tempGate in Anon Scope = ${EvaluateGateExpression("tempGate")}") // Should evaluate (0.9 * 0.2) + 0.3

      // Test the temporary variable in tempGate
      println(s"Anonymous Scope tempGate = ${TestGate("tempGate", "tempVar")}") // Should return 0.11

      // Test the modified A in logicGate1 within the anon scope
      println(s"TestGate result for A in Anon Scope logicGate1 = ${TestGate("logicGate1", "A")}") // Should return 0.29 since scope of logicGate1 exists
    }

    // Test Y after exiting the anonymous scope
    println(s"Global Y after Anon Scope = ${TestGate("global", "Y")}") // Should return 0.4

    // Test tempGate after exiting the anonymous scope (shouldn't exist) and SHOULD throw an error
    try
      println(s"Anonymous Scope tempGate = ${TestGate("tempGate", "tempVar")}")
    catch
      case e: Exception =>
        println(s"Error during TestGate for tempGate: ${e.getMessage}")

    // Test logicGate1 after exiting the anonymous scope (A should revert)
    println(s"TestGate result for A after Anon Scope in logicGate1 = ${TestGate("logicGate1", "A")}") // Should return 0.5
