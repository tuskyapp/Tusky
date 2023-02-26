import java.io.ByteArrayOutputStream
import javax.inject.Inject

// Must wrap this in a ValueSource in order to get well-defined fail behavior without confusing Gradle on repeat builds.
abstract class GitShaValueSource : ValueSource<String, ValueSourceParameters.None> {
    @Inject abstract fun getExecOperations(): ExecOperations

    override fun obtain(): String {
        try {
            val output = ByteArrayOutputStream()

            getExecOperations().exec {
                commandLine("git", "rev-parse", "--short=8", "HEAD")
                standardOutput = output
            }
            return output.toString().trim()
        } catch (ignore: GradleException) {
            // Git executable unavailable, or we are not building in a git repo. Fall through:
        }
        return "unknown"
    }
}

// Export closure
project.extra["getGitSha"] = {
    providers.of(GitShaValueSource::class) {}.get()
}
