/* Copyright (C) 2008-2014 University of Massachusetts Amherst.
   This file is part of "FACTORIE" (Factor graphs, Imperative, Extensible)
   http://factorie.cs.umass.edu, http://github.com/factorie
   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at
    http://www.apache.org/licenses/LICENSE-2.0
   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package cc.factorie.util

import scala.collection.mutable
import scala.collection.JavaConverters._
import java.util

// Java hash mutable hashmaps are significantly faster than the Scala ones, for no good reason
// Replace this with AnyRefMap when we switch to Scala 2.11 -luke
object JavaHashMap {
  def apply[K, V](): mutable.Map[K, V] = new util.HashMap[K, V]().asScala
}
