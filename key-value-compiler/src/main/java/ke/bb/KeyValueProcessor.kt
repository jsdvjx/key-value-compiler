package ke.bb

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSTypeReference

class KeyValueProcessor(
    environment: SymbolProcessorEnvironment,
) : SymbolProcessor {
    private val codeGenerator = environment.codeGenerator

    data class Property(
        val name: String,
        val type: String,
        val isMutable: Boolean,
    )

    class Template {
        companion object {
            private val variableMap = mutableMapOf<String, String>()
            private const val TEMPLATE: String = """package {PACKAGE_NAME}

class {CLASS_NAME}Impl(private val control: ke.bb.Control) : {CLASS_NAME} {
    private val className = "{CLASS_NAME}"
    private var map: MutableMap<String, Any?>
    private val methods = arrayOf({METHODS_LOOP})
    private val defaultValues: Array<String?> = arrayOf({DEFAULT_VALUES_LOOP})
    private val types = arrayOf({TYPES_LOOP})
    
    init {
        methods.forEachIndexed { index, method ->
            control.initKeyValue(className, method, types[index], defaultValues[index])
        }
        map = control.getGroup(className)
    }

    
    {DELEGATE_LOOP}
}
"""

            private const val DELEGATE_TEMPLATE: String = "override {VAL_VAR} {PROP_NAME}: {TYPE_NAME} by map"


            private const val VAL_VAR_STR: String = "{VAL_VAR}"
            private const val PROP_NAME_STR: String = "{PROP_NAME}"
            private const val TYPE_NAME_STR: String = "{TYPE_NAME}"
            private const val METHODS_LOOP_STR: String = "{METHODS_LOOP}"
            private const val CLASS_NAME_STR: String = "{CLASS_NAME}"
            private const val PACKAGE_NAME_STR: String = "{PACKAGE_NAME}"
            private const val DEFAULT_VALUES_LOOP_STR: String = "{DEFAULT_VALUES_LOOP}"
            private const val TYPES_LOOP_STR: String = "{TYPES_LOOP}"
            private const val DELEGATE_LOOP_STR: String = "{DELEGATE_LOOP}"

            var METHODS_LOOP: String by variableMap
            var CLASS_NAME: String by variableMap
            var PACKAGE_NAME: String by variableMap
            var DEFAULT_VALUES_LOOP: String by variableMap
            var TYPES_LOOP: String by variableMap
            private val properties: MutableList<Property> = mutableListOf()
            private val DELEGATE_LOOP: String
                get() {
                    return properties.joinToString("\n    ") { property ->
                        DELEGATE_TEMPLATE
                            .replace(VAL_VAR_STR, if (property.isMutable) "var" else "val")
                            .replace(PROP_NAME_STR, property.name)
                            .replace(TYPE_NAME_STR, property.type)
                    }
                }

            fun prop(name: String, type: String, isMutable: Boolean) {
                properties.add(Property(name, type, isMutable))
            }

            fun compile(): String {
                return TEMPLATE
                    .replace(CLASS_NAME_STR, CLASS_NAME)
                    .replace(PACKAGE_NAME_STR, PACKAGE_NAME)
                    .replace(METHODS_LOOP_STR, METHODS_LOOP)
                    .replace(DEFAULT_VALUES_LOOP_STR, DEFAULT_VALUES_LOOP)
                    .replace(TYPES_LOOP_STR, TYPES_LOOP)
                    .replace(DELEGATE_LOOP_STR, DELEGATE_LOOP)
            }


        }
    }



    private fun getType(type: KSTypeReference): String {
        return if ((type.element?.typeArguments?.size ?: 0) > 0) {
            "${type.element.toString()}<${type.element?.typeArguments?.joinToString(",") { getType(it.type!!) }}>"
        } else {
            type.element.toString()
        } + type.resolve().isMarkedNullable.let { if (it) "?" else "" }
    }


    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver.getSymbolsWithAnnotation("ke.bb.KeyValueMirror").map {
            it as KSClassDeclaration
        }.forEach {
            Template.run {
                CLASS_NAME = it.simpleName.asString()
                PACKAGE_NAME = it.packageName.asString()
                METHODS_LOOP = it.getAllProperties().map { property ->
                    """"${property.simpleName.asString()}""""
                }.joinToString(", ")
                DEFAULT_VALUES_LOOP = it.getAllProperties().map { property ->
                    property.annotations.firstOrNull {
                        it.shortName.asString().endsWith("DefaultValue")
                    }?.arguments?.first()?.value?.let {
                        "\"$it\""
                    }
                }.joinToString(", ")
                TYPES_LOOP = it.getAllProperties().map { property ->
                    """"${getType(property.type)}""""
                }.joinToString(", ")
                it.getAllProperties().forEach { property ->
                    prop(property.simpleName.asString(), getType(property.type), property.isMutable)
                }
                val content = compile()
                codeGenerator.createNewFile(
                    dependencies = Dependencies(false),
                    packageName = PACKAGE_NAME,
                    fileName = "${CLASS_NAME}Impl"
                ).bufferedWriter().use { bw ->
                    bw.write(content)
                }
            }
        }
        return emptyList()
    }
}
