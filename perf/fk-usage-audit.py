import re,glob,sys
root='src/main/java'
def camel(s):
    p=s.split('_'); return p[0]+''.join(x.title() for x in p[1:])
src={f:open(f).read() for f in glob.glob(root+'/**/*.java',recursive=True)}
entities={}
for f,t in src.items():
    m=re.search(r'@Table\(\s*name\s*=\s*"(\w+)"',t)
    if m and '@Entity' in t:
        cls=re.search(r'class\s+(\w+)',t).group(1)
        entities[m.group(1)]=(cls,f)
repos=[(f,t) for f,t in src.items() if re.search(r'interface\s+\w+\s+extends\s+\w*Repository',t) or 'JpaSpecificationExecutor' in t]
def prop(table,col):
    cls,f=entities[table]
    t=src[f]
    for m in re.finditer(r'((?:@\w+(?:\([^)]*\))?\s*)+)private\s+[\w<>,.? ]+?\s+(\w+)\s*(?:=[^;]*)?;',t):
        ann,name=m.group(1),m.group(2)
        j=re.search(r'@(?:JoinColumn|Column)\(\s*name\s*=\s*"(\w+)"',ann) or re.search(r'@(?:JoinColumn|Column)\([^)]*?name\s*=\s*"(\w+)"',ann)
        if j and j.group(1)==col: return name
        if not j and ('@ManyToOne' in ann or '@OneToOne' in ann) and col==re.sub(r'([A-Z])',lambda x:'_'+x.group(1).lower(),name)+'_id': return name
    for m in re.finditer(r'private\s+[\w<>,.? ]+?\s+(\w+)\s*(?:=[^;]*)?;',t):
        if re.sub(r'([A-Z])',lambda x:'_'+x.group(1).lower(),m.group(1))==col: return m.group(1)
    return None
for line in open(sys.argv[1]).read().split():
    table,col=line.split('.')
    if table not in entities: print('##',line,'NO_ENTITY'); continue
    cls,f=entities[table]
    p=prop(table,col)
    if not p: print('##',line,cls,'NO_PROP'); continue
    P=p[0].upper()+p[1:]
    base=P[:-2] if P.endswith('Id') else P
    hits=[]
    derived=re.compile(r'\b(?:find|exists|delete|count|read|get|stream|query)\w*By\w*?(?:(?<=By)|(?<=And)|(?<=Or)|(?<=_))%s(?:Id)?(?=[A-Z_(]|\b)'%re.escape(P if not P.endswith('Id') else base))
    for rf,rt in repos:
        if not re.search(r'<\s*%s\s*,'%cls,rt) and cls not in rt: continue
        for i,ln in enumerate(rt.split('\n')):
            if derived.search(ln) and re.search(r'<\s*%s\s*,'%cls,rt): hits.append(('DERIVED',rf.split('/')[-1],i+1,ln.strip()[:120]))
    qre=re.compile(r'\b\w+\.%s(?:\.id)?\b'%re.escape(p))
    for rf,rt in src.items():
        for m in re.finditer(r'@Query\((.*?)\)\s*\n',rt,re.S):
            q=m.group(1)
            if re.search(r'\b%s\b'%cls,q) and qre.search(q):
                hits.append(('JPQL',rf.split('/')[-1],rt[:m.start()].count('\n')+1,re.sub(r'\s+',' ',q)[:150]))
            if table in q and re.search(r'\b%s\b'%col,q):
                hits.append(('NATIVE',rf.split('/')[-1],rt[:m.start()].count('\n')+1,re.sub(r'\s+',' ',q)[:150]))
    print('##',line,cls+'.'+p,len(hits))
    for h in hits[:6]: print('    ',*h)
