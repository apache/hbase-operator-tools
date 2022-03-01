# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set terminal svg dynamic
set title 'Percentiles Row Sizes' font ",36"
set key font ",5"
set autoscale
plot \
"table.rowSize.percentiles.4986160210664496121.gnuplotdata" title "table regions=589, duration=PT38.262S, N=35751, min=1136.0, max=2.38644832E8" with lines
