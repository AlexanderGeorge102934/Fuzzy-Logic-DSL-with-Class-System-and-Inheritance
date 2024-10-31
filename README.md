# Fuzzy Logic DSL with Class System and Inheritance

## Overview
This project implements a Domain-Specific Language (DSL) for handling fuzzy logic operations and includes an object-oriented class system with support for inheritance, method overriding, and nested classes. The DSL allows for creating and managing fuzzy variables, applying common fuzzy logic operations (such as AND, OR, ADD, MULT, etc.), and scoping variables both globally and within specific "gates". Gates represent logical contexts in which different operations can be performed. The DSL includes functionality for handling variable scoping, assigning variables and expressions to gates, and evaluating expressions assigned to gates. It also uses the FuzzyExpr class as the base abstraction for defining and evaluating fuzzy expressions, encapsulating various fuzzy logic operations.
## Key Concepts

1. **Fuzzy Variables and Values**:
    - A **Fuzzy Variable** represents values that can range between 0 and 1. These variables can be assigned specific values using the `Assign` function. Assignments can be global or scoped to a particular gate.
    - A **Fuzzy Value** is a numerical value between 0 and 1 that represents a degree of truth.

2. **Operations**:
    - **Fuzzy Expressions** are operations or transformations applied to fuzzy variables. The DSL supports various fuzzy operations like:
        - **Addition (ADD)**: Sum of two variables, capped at 1.0.
        - **Multiplication (MULT)**: Product of two variables.
        - **Complement (COMPLEMENT)**: Complement of a variable, equivalent to 1 minus the variable's value.
        - **AND (FuzzyAND)**: Takes the minimum of two variables.
        - **OR (FuzzyOR)**: Takes the maximum of two variables.
        - **XOR (FuzzyXOR)**: The absolute difference between two variables.
        - **Alpha Cut (ALPHA_CUT)**: A threshold operation that returns 0 if the variable is below the threshold, otherwise returns the variable's value.
3. **Class System and Inheritance**:
    - The DSL includes an object-oriented class system that supports class definitions, inheritance, method definitions, method overriding, and nested classes.
        - **Classes** can be defined with variables and methods.
        - **Inheritance** allows a class to inherit variables and methods from a parent class.
        - **Method Overriding** enables a subclass to provide a specific implementation of a method that is already defined in its superclass.
        - **Nested Classes** are classes defined within another class, allowing for hierarchical structuring.

4. **Scopes**:
    - Variables can exist either in a **global scope** or within **local scopes**, referred to as **gates**. Gates represent logical contexts where variables can be defined and fuzzy logic expressions can be assigned and evaluated.

5. **Anonymous Scopes**:
    - Temporary environments that allow you to execute code within a block, ensuring that changes within the block do not persist once the block exits.

6. **Assigning Variables and Expressions**:
    - You can assign fuzzy variables to values and assign fuzzy expressions to gates.

## Key DSL Functions

### 1. **Class Definitions**:
- In the DSL you can define classes with or without inheritance from another class. Classes with inheritance can only inherit from one other class.
- **Syntax for defining classes**: `val ClassName = Class("ClassName") {// Class variables and methods}`
  ```scala
  val BaseClass = Class("Base") {
     ClassVar("var", "double")
     // Define methods
  }
  ```
### 2. **Class Definitions with Inheritance**:
- In the DSL you can define classes with inheritance from another class. Classes with inheritance can only inherit from one other class.
- **Syntax for defining classes with inheritance**: `val SubClass = Class("SubClass", Extends(ParentClass)) {// Additional variables and methods}`
  ```scala
  val DerivedClass = Class("Derived", Extends(BaseClass)) {
     // Additional variables and methods
  }
  ```
### 3. **Defining Class Variables**:
- By default, class variables will have a value of 0.0. To change the value they must be changed within class methods or when they are instantiated. They cannot be assigned values by itself in the class. All class variables must be of type double (FuzzyVar)
- **Syntax for defining class variables**: `ClassVar("variableName", "variableType")` so all VarTypes are of double
  ```scala
  ClassVar("var", "double")
  ```

### 4. **Class Methods**:
- In the DSL you can define class methods within classes which can be accessed by each instance. To define a method you must define the method name, then list the parameters with the Parameter construct inside a list, and finally list your statements and return a Fuzzy Expr. You must return a fuzzy expression i.e a double. You can also override methods if the class is a subclass of another.
- **Syntax for defining class method**: `DefineMethod("methodName", List(Parameter("param1", "type"), ...), List(// Statements))`
  ```scala
   DefineMethod("m1", List(Parameter("p1", "double")), List(
     Assign("somevar", ADD(FuzzyVariable("var"), FuzzyVariable("p1"))),
     Return(FuzzyVariable("somevar"))
   ))

  DefineMethod("m2", List(), List(
     Assign("somevar", ADD(FuzzyValue(0.5), FuzzyValue(0.2))),
     Return(FuzzyVariable("somevar"))
   ))
  ```

### 5. **Nested Classes**:
- In the DSL you can also include nested classes within another class
- **Syntax for defining class method**: `// Inside the outer class definition Class("InnerClassName") {// Inner class variables and methods}`
  ```scala
   val OuterClass = Class("Outer") {
     ClassVar("outerVar", "double")
     Class("Inner") {
       // Inner class definitions
     }
   }
  ```

### 6. **Accessing Outer Variables from Inner Class**:
- Inner classes can access variables from their outer classes if properly set up.
  ```scala
   Class("Inner") {
     DefineMethod("innerMethodWithOuterVar", List(), List(
       Assign("innerVar", ADD(FuzzyVariable("innerVar"), FuzzyVariable("outerVar"))),
       Return(FuzzyVariable("innerVar"))
     ))
   }
  ```
### 7. **Creating Instances with Outer References:**:
- When creating an instance of an inner class, pass the outer instance as a reference.
- **Syntax for defining class method**: `val innerInstance = CreateNew(InnerClass, Some(outerInstance))`
  ```scala
   val outerInstance = CreateNew(OuterClass)
   val innerInstance = CreateNew(InnerClass, Some(outerInstance))
  ```

### 8. **Invoking Methods that Access Outer Variables:**:
- Invoke methods as usual; the method can access outerVar via the outer instance.
  ```scala
   val result = InvokeMethod(innerInstance, "innerMethodWithOuterVar", Map())
  ```

### 9. **Assigning Variables**:
- By default, variables are assigned to the **global scope** if not enclosed within a gate-specific `Scope` block.
- **Syntax for global assignment**: `Assign(FuzzyVariable, FuzzyValue)`
  ```scala
  Assign(FuzzyVariable("X"), FuzzyValue(0.2)) // Global assignment
  ```
- **Syntax for gate-scoped assignment**: `Scope(Gate){Assign(FuzzyVariable, FuzzyValue)}`
  ```scala
  Scope(Gate("myGate")) {
    Assign(FuzzyVariable("A"), FuzzyValue(0.5)) // A is scoped within myGate
  }
  ```

### 10. **Assigning Expressions to Gates**:
You can assign a fuzzy expression to a gate, allowing the gate to evaluate the expression.

- **Syntax**: `Assign(Gate, Expression)(using Gate)`
  ```scala
  Assign(Gate("logicGate1"), ADD(FuzzyVariable("A"), FuzzyVariable("B")))(using Gate("logicGate1"))
  ```
- **Gate**: `Gate("logicGate1")` defines the scope where the fuzzy logic expression will be evaluated.
- **Fuzzy Expression**: The fuzzy logic operation like `ADD(FuzzyVariable("A"), FuzzyVariable("B"))` performs an addition between fuzzy variables A and B (or other operations like `MULT`, `AND`, etc.).
- **Fuzzy Variables**: Variables (`FuzzyVariable("A")`, `FuzzyVariable("B")`) must be assigned values in the same gate or global scope before assigning an expression to the gate.
- **`using Gate("logicGate1")`**: This defines the scope for the gate where the fuzzy expression will be evaluated.

### 11. **Testing Variables**:
- You can use `TestGate` to check the value of a variable within a specific scope.
- **Syntax for global scope**:
  ```scala
  TestGate("global", "X") // Check value of X within the global scope 
  ```
- **Syntax for gate-specific scope**:
  ```scala
  TestGate("myGate", "A") // Check value of A within the specific gate "myGate"
  ```

### 12. **Evaluating Gate Expressions**:
- Once an expression is assigned to a gate, you can evaluate it using `EvaluateGateExpression`.
- **Syntax**:
  ```scala
  EvaluateGateExpression("logicGate1") // Will return the numeric value of the expression of logicGate1 
  ```

### 13. **Anonymous Scope Usage**:
- Anonymous scopes allow temporary modifications to the environment. Once the scope is exited, all changes are reverted.
- **Syntax**:
  ```scala
  AnonymousScope {
    // Temporary assignments and operations
    Scope(Gate("tempGate")) {Assign(FuzzyVariable("tempVar"), FuzzyValue(0.11))}
    Assign(FuzzyVariable("Y"), FuzzyValue(0.9))
  }
  ```
## Error Handling
- **Out-of-scope Variables**: If you attempt to retrieve a variable that has not been assigned or is out of scope, the system will throw an error:
  ```scala
  throw new Exception(s"Variable $name not found globally or in gate $gateName")
  ```
- **Unassigned Gate Expressions**: If you try to evaluate a gate that does not have an assigned expression, the system will throw an error:
  ```scala
  throw new Exception(s"No expression assigned to gate $gateName")
  ```
- **Missing Parameters**: Suppose you attempt to invoke a method with a missing parameter:val result =
  ```scala
   InvokeMethod(derivedInstance, "m1", Map("p1" -> 0.5)) // Missing "p2"
  ```
    - This will throw an error: `Exception: Missing argument for parameter p2`



## Project Structure
- `src/main/scala/main.scala` – Main code for the project.
- `src/test/scala/FuzzyLogicTest.scala` – Test cases for fuzzy logic operations.
- `build.sbt` – SBT configuration file for building and managing dependencies.

## Getting Started

### Prerequisites
- Scala 2.13.x or higher
- SBT (Simple Build Tool) installed SBT version 1.10.1
- IntelliJ IDEA

### Setting Up Project
To set this project up you need to
- Clone the project repository to your local machine
- Open the cloned project in IntelliJ IDEA
    - Navigate to the cloned repository folder and open it
    - IntelliJ will automatically detect the build.sbt file and set up the project dependencies
    - If prompted, click "Enable Auto-Import" to automatically download and manage the Scala dependencies via SBT
- Ensure that you have the Java Development Kit (JDK) installed (between versions 8 and 22)
- In the terminal inside IntelliJ, navigate to the project root and run the following SBT commands to ensure everything is properly set up
    - ```sbt clean compile```

### Running the Project
- To compile the project, run the following command in the terminal
    - ```sbt compile```
- To run the main program
    - ```sbt run```
- To run FuzzyLogicTests
    - ```sbt test```
