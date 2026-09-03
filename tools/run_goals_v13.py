from pathlib import Path

patch = Path('tools/patch_goals_v13.py')
code = patch.read_text(encoding='utf-8')

# Keep renderSourceCard() intact. In the v1.1-generated Java source that method
# sits between renderMain() and renderSelectors(); the original v1.3 regex was
# broad enough to consume it together with renderMain().
code = code.replace(
    r'(?=    void renderSelectors\(\))',
    r'(?=    void renderSourceCard\(\))',
    1
)

# Fix the period-block boundary regex in the generated patch before execution.
code = code.replace(r'(?=    View statLine\(\))', r'(?=    View statLine\()')

exec(compile(code, str(patch), 'exec'))
