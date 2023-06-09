# Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

java -Dlogback.configurationFile="./config/logback.xml" -cp bin\SBFT.jar;lib\slf4j-api-1.7.25.jar;lib\logback-core-1.2.3.jar;lib\netty-all-4.1.30.Final.jar;lib\logback-classic-1.2.3.jar;lib\core-0.1.4.jar;lib\commons-codec-1.11.jar %1 %2 %3 %4 %5 %6 %7 %8 %9