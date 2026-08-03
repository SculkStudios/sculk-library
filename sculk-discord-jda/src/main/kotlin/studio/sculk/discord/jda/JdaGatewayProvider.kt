package studio.sculk.discord.jda

import kotlinx.coroutines.CoroutineScope
import studio.sculk.annotation.SculkInternal
import studio.sculk.discord.BotConfig
import studio.sculk.discord.DiscordGateway
import studio.sculk.discord.DiscordGatewayProvider

/**
 * Found by [studio.sculk.discord.SculkDiscord] through `META-INF/services`.
 *
 * Public because a ServiceLoader has to instantiate it, and it needs a no-argument constructor for
 * the same reason. Nothing else should name it — depend on the module and let discovery do this.
 */
@SculkInternal
public class JdaGatewayProvider : DiscordGatewayProvider {
    override val backend: String = "JDA"

    /**
     * Whether JDA is actually on the classpath.
     *
     * The adapter module can be present while JDA is not: it declares JDA `compileOnly`, so a Paper
     * plugin that forgot the `libraries:` entry gets exactly this shape. Reporting it here turns a
     * `NoClassDefFoundError` mid-startup into a named failure at discovery.
     */
    override fun isAvailable(): Boolean =
        runCatching { Class.forName("net.dv8tion.jda.api.JDABuilder", false, javaClass.classLoader) }.isSuccess

    override fun create(config: BotConfig, scope: CoroutineScope): DiscordGateway = JdaGateway(config, scope)
}
