export const RAW_ELEMENT_COMPONENTS = {
  table: 'Table (@/components/ui/table)',
  select: 'Select o NativeSelect (@/components/ui/select, @/components/ui/native-select)',
  textarea: 'Textarea (@/components/ui/textarea)',
  input: 'Input (@/components/ui/input)',
}

export const EXEMPT_INPUT_TYPES = ['checkbox', 'radio', 'file', 'hidden']

export const RAW_ELEMENT_EXCEPTIONS = [
  'src/features/attendance/pages/DocenteTomarAsistencia.jsx',
  'src/features/evaluation/components/GradeGridTable.jsx',
]

function literalType(node) {
  const attribute = node.attributes.find(
    (candidate) => candidate.type === 'JSXAttribute' && candidate.name.name === 'type',
  )
  return attribute?.value?.type === 'Literal' ? attribute.value.value : null
}

export const noRawElementsRule = {
  meta: { type: 'problem', schema: [] },
  create(context) {
    return {
      JSXOpeningElement(node) {
        if (node.name.type !== 'JSXIdentifier') return
        const tag = node.name.name
        if (!Object.hasOwn(RAW_ELEMENT_COMPONENTS, tag)) return
        if (tag === 'input' && EXEMPT_INPUT_TYPES.includes(literalType(node))) return
        context.report({
          node,
          message: `No uses <${tag}> crudo: usá ${RAW_ELEMENT_COMPONENTS[tag]} para que un cambio de diseño aplique en todos los usos.`,
        })
      },
    }
  },
}

export const localPlugin = { rules: { 'no-raw-elements': noRawElementsRule } }
