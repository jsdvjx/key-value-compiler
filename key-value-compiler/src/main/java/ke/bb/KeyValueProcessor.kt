package ke.bb

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration

class KeyValueProcessor(
    private val environment: SymbolProcessorEnvironment,
) : SymbolProcessor {
    private val codeGenerator = environment.codeGenerator

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val propertyResolver = { classDeclaration: KSClassDeclaration, property: KSPropertyDeclaration ->
            property.run {
                """
                    override ${if (isMutable) "var" else "val"} ${simpleName.asString()}: ${type.resolve().declaration.qualifiedName?.asString()} = ""
                        get() = control.get("${classDeclaration.simpleName.asString()}","${simpleName.asString()}")!!${
                    if (isMutable) """
                        set(value) { 
                            control.set("${classDeclaration.simpleName.asString()}","${simpleName.asString()}", value)
                            field = value
                        }
                    """ else ""
                }
                    """
            }

        }
        resolver.getSymbolsWithAnnotation("ke.bb.KeyValueMirror").map {
            it as KSClassDeclaration
        }.forEach {
            val content = """
                    package ${it.containingFile!!.packageName.asString()}
                    
                    class ${it.simpleName.asString()}Impl(private val control: ke.bb.Control) : ${it.simpleName.asString()} {${
                it.getAllProperties().joinToString("\n") { property ->
                    propertyResolver(it, property)
                }
            }
                    }
                    """.trimIndent()
            codeGenerator.createNewFile(
                Dependencies(false, *arrayOf()),
                it.containingFile?.packageName!!.asString(),
                "${it.simpleName.asString()}Impl"
            )
                .bufferedWriter().use { bw ->
                    bw.write(content)
                }
        }
        return emptyList()
    }
}