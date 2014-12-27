package org.koiroha.bombaysapphire.tools

import org.koiroha.bombaysapphire.DBAccess

import scala.slick.codegen.SourceCodeGenerator

object Schema extends DBAccess {
	def main(args: Array[String]): Unit = SourceCodeGenerator.main(
		Array(
			Config.slickDriver,
			Config.jdbcDriver,
			Config.url,
			"src/main/scala",
			"org.koiroha.bombaysapphire.schema",
			Config.username, Config.password
		)
	)
}