package org.koiroha.bombaysapphire.garuda.tools

import java.io.File

import org.koiroha.bombaysapphire.garuda.Context

import scala.slick.codegen.SourceCodeGenerator

object Schema {
	def main(args:Array[String]): Unit = {
		val context = Context(new File(args.head))
		SourceCodeGenerator.main(
			Array(
				classOf[scala.slick.driver.PostgresDriver].getName,
				context.databaseConfig.driver,
				context.databaseConfig.url,
				"garuda/src/main/scala",
				"org.koiroha.bombaysapphire.garuda.schema",
				context.databaseConfig.username, context.databaseConfig.password
			)
		)
	}
}