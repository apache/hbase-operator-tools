<!--
 Licensed to the Apache Software Foundation (ASF) under one
 or more contributor license agreements.  See the NOTICE file
 distributed with this work for additional information
 regarding copyright ownership.  The ASF licenses this file
 to you under the Apache License, Version 2.0 (the
 "License"); you may not use this file except in compliance
 with the License.  You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->

# Base

Some values such as SERVICE name, SERVICEACCOUNT name,
and RBAC role are hard-coded in the environment-configmap.yaml
and supplied into the pods as environment variables. Other
hardcodings include the service name ('hadoop') and the
namespace we run in (also 'hadoop').

The hadoop Configuration system can interpolate environment variables
into '\*.xml' file values ONLY.  See
[Configuration Javadoc](http://hadoop.apache.org/docs/current/api/org/apache/hadoop/conf/Configuration.html)

...but we can not do interpolation of SERVICE name into '\*.xml' file key names
as is needed when doing HA in hdfs-site.xml... so for now, we have
hard-codings in 'hdfs-site.xml' key names.  For example, the property key name
`dfs.ha.namenodes.hadoop` has the SERVICE name ('hadoop') in it or the key
`dfs.namenode.http-address.hadoop` (TODO: Fix/Workaround).

Edit of pod resources or jvm args for a process are
done in place in the yaml files or in kustomization
replacements in overlays.
