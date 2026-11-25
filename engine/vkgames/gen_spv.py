"""Writes mlp_spv.h from out/mlp_<TM><TN>.spv (build_shaders.sh). Usage: python3 -I gen_spv.py TM TN [TM TN ...]"""
import struct, sys
v = sys.argv[1:]
out = ['/* generated from mlp.comp by build.sh */']
names = []
for i in range(0, len(v), 2):
    t = v[i] + v[i + 1]
    b = open('out/mlp_%s.spv' % t, 'rb').read()
    w = struct.unpack('<%dI' % (len(b) // 4), b)
    out.append('static const unsigned int mlp_spv_%s[] = {\n' % t + ',\n'.join(', '.join('0x%08x' % x for x in w[j:j + 8]) for j in range(0, len(w), 8)) + '\n};')
    names.append('{"%s", %s, %s, sizeof mlp_spv_%s, mlp_spv_%s}' % (t, v[i], v[i + 1], t, t))
out.append('static const struct { const char *name; int tm, tn; size_t size; const unsigned int *code; } mlp_variants[] = {\n    ' + ',\n    '.join(names) + '\n};')
open('mlp_spv.h', 'w').write('\n'.join(out) + '\n')
