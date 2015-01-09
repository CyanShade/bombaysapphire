package org.koiroha.bombaysapphire.tools

import scala.slick.codegen.SourceCodeGenerator

object Schema {
	def main(args: Array[String]): Unit = SourceCodeGenerator.main(
		Array(
			Context.Database.slickDriver,
			Context.Database.jdbcDriver,
			Context.Database.url,
			"src/main/scala",
			"org.koiroha.bombaysapphire.schema",
			Context.Database.username, Context.Database.password
		)
	)
}