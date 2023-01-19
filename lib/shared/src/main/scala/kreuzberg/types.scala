package kreuzberg

import kreuzberg.util.Stateful

type AssemblyResult = Stateful[AssemblyState, Assembly]

type NodeResult[T] = Stateful[AssemblyState, ComponentNode[T]]

// Imperative often used components

type SimpleComponentBase = imperative.SimpleComponentBase

type SimpleContext = imperative.SimpleContext
