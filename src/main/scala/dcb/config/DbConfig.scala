package dcb.config

import pureconfig.ConfigReader
import pureconfig.generic.derivation.default.*

final case class DbConfig(
    host: String,
    port: Int,
    user: String,
    password: String,
    database: String,
    max: Int
) derives ConfigReader
