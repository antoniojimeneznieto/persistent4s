package dcb.config

import pureconfig.ConfigReader
import pureconfig.generic.derivation.default.*

final case class AppConfig(
    eventLog: DbConfig,
    views: DbConfig
) derives ConfigReader
