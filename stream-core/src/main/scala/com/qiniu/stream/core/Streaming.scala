/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.qiniu.stream.core

import com.qiniu.stream.core.config.{FilePipelineConfig}
import scopt.OptionParser


object Streaming extends App {

  val cliParser: OptionParser[FilePipelineConfig] = new OptionParser[FilePipelineConfig]("QStreaming") {
    head("QStreaming")

    opt[String]('j', "job")
      .required()
      .text("Path to the pipeline dsl file")
      .action((file, c) => c.copy(jobDsl = file))

    opt[Map[String, String]]('c',"conf").valueName("k1=v1, k2=v2...")
      .optional()
      .action((vars, c) => c.copy(args = vars))
      .text("variables of pipeline dsl file")

    help("help") text "use command line arguments to specify the configuration file path or content"
  }

  val config = cliParser.parse(args, FilePipelineConfig()).getOrElse(FilePipelineConfig())
  PipelineRunner(config).run()
}
