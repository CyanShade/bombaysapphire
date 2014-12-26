package org.koiroha.bombaysapphire.tools

import scala.slick.codegen.SourceCodeGenerator

object Schema {
	def main(args: Array[String]): Unit = SourceCodeGenerator.main(
		Array(
			"scala.slick.driver.PostgresDriver",
			"org.postgresql.Driver",
			"jdbc:postgresql://localhost:5433/bombaysapphire",
			"src/main/scala",
			"org.koiroha.bombaysapphire.schema",
			"postgres", "postgres"
		)
	)
}