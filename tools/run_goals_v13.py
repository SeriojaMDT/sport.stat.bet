from pathlib import Path

patch = Path('tools/patch_goals_v13.py')
code = patch.read_text(encoding='utf-8')
# Fix the period-block boundary regex in the generated patch before execution.
code = code.replace(r'(?=    View statLine\(\))', r'(?=    View statLine\()')
exec(compile(code, str(patch), 'exec'))
