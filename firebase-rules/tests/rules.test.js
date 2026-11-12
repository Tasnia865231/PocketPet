const t=require('targaryen');
const rules=require('../database.rules.json');
const TS={'.sv':'timestamp'};
let off=0; const _rn=Date.now; Date.now=()=>_rn()+off; let NOW=Date.now();
let db=t.database(rules,{
  status:{ A:{state:'online',lastChanged:1,displayName:'Ann'}, B:{state:'online',lastChanged:1,displayName:'Bob'}, C:{state:'offline',lastChanged:1,displayName:'Cy'} }
},NOW);
const A={uid:'A'},B={uid:'B'},C={uid:'C'};
let pass=0,fail=0;
function show(name,r,expect){
  const ok=!!r.allowed===expect;
  ok?pass++:fail++;
  console.log((ok?'PASS ':'FAIL ')+name+(ok?'':'  -> '+(r.info||'').toString().split('\n').slice(0,6).join(' | ')));
  return r;
}
function w(who,path,val,name,expect=true){ const r=db.as(who).write(path,val); show(name,r,expect); if(r.allowed&&expect) db=r.newDatabase; return r;}
function u(who,path,val,name,expect=true){ const r=db.as(who).update(path,val); show(name,r,expect); if(r.allowed&&expect) db=r.newDatabase; return r;}
function tick(ms){off+=ms; NOW=Date.now();}

// ---- invites
w(A,'/invites/B/A',{fromName:'Ann',toName:'Bob',state:'pending',createdAt:TS},'A invites online B');
w(A,'/invites/C/A',{fromName:'Ann',toName:'Cy',state:'pending',createdAt:TS},'A cannot invite offline C',false);
w(A,'/invites/A/A',{fromName:'Ann',toName:'Ann',state:'pending',createdAt:TS},'cannot invite yourself',false);
w(B,'/invites/B/A',{fromName:'Ann',toName:'Bob',state:'pending',createdAt:TS},'B cannot write invite as A',false);
tick(5000);
w(A,'/invites/B/A',{fromName:'Ann',toName:'Bob',state:'pending',createdAt:TS},'duplicate live invite blocked',false);
w(C,'/invites/B/A/state','accepted','stranger C cannot accept',false);
u(B,'/invites/B/A',{state:'accepted',gameId:'G1'},'B accepts invite');
// ---- game create
const base={x:'A',o:'B',xName:'Ann',oName:'Bob',phase:'playing',round:1,turn:'X',sx:0,so:0,createdAt:TS};
w(A,'/games/G1',base,'A (inviter) cannot create game',false);
w(B,'/games/G1',{...base,x:'C'},'B cannot create game with wrong x',false);
w(B,'/games/G1',{...base,sx:1},'B cannot create with score',false);
w(B,'/games/G1',base,'B creates game G1');
w(C,'/games/G1/turn','O','outsider cannot write',false);
w(A,'/playing/A','G1','A sets playing flag');
w(B,'/playing/B','G1','B sets playing flag');
tick(10000);
w(C,'/invites/B/C',{fromName:'Cy',toName:'Bob',state:'pending',createdAt:TS},'cannot invite a busy player (and C offline)',false);
// ---- moves
function mv(who,cell,sym,extra={},name,expect=true){ return u(who,'/games/G1',{['board/'+cell]:sym,turn:sym==='X'?'O':'X',...extra},name,expect);}
mv(B,4,'O',{},'O cannot move first',false);
mv(A,0,'X',{},'X moves cell 0');
mv(A,1,'X',{},'X cannot move twice',false);
mv(B,0,'O',{},'O cannot overwrite cell 0',false);
u(B,'/games/G1',{'board/4':'X',turn:'X'},'O cannot place X symbol',false);
u(B,'/games/G1',{'board/4':'O'},'O cannot skip passing turn',false);
u(B,'/games/G1',{'board/4':'O','board/5':'O',turn:'X'},'O cannot place two cells',false);
mv(B,4,'O',{},'O moves cell 4');
mv(A,1,'X',{},'X moves cell 1');
mv(B,5,'O',{},'O moves cell 5');
// fake win: X claims round over without a line
u(A,'/games/G1',{'board/8':'X',phase:'roundEnd',r1:'X',sx:1,endedAt:TS},'fake win without a line',false);
// real win: X plays 2 -> 0,1,2
u(A,'/games/G1',{'board/2':'X',phase:'roundEnd',r1:'X',sx:1,endedAt:TS},'X wins round 1 (0,1,2)');
u(A,'/games/G1',{'board/3':'X',turn:'O'},'no moves after roundEnd',false);
u(A,'/games/G1',{phase:'playing',round:2,turn:'X',board:null},'cannot advance too early',false);
tick(3000);
u(B,'/games/G1',{phase:'playing',round:2,turn:'X',board:null,sx:5},'advance cannot change score',false);
u(B,'/games/G1',{phase:'playing',round:2,turn:'X',board:null},'B advances to round 2');
u(A,'/games/G1',{phase:'playing',round:3,turn:'X',board:null},'cannot skip round again',false);
// round 2: O wins (X plays 0,1,8 ; O plays 3,4,5)
mv(A,0,'X',{},'r2 X0'); mv(B,3,'O',{},'r2 O3'); mv(A,1,'X',{},'r2 X1'); mv(B,4,'O',{},'r2 O4'); mv(A,8,'X',{},'r2 X8');
u(B,'/games/G1',{'board/5':'O',phase:'roundEnd',r2:'O',so:1,endedAt:TS},'O wins round 2 (3,4,5)');
tick(3000);
u(A,'/games/G1',{phase:'playing',round:3,turn:'X',board:null},'advance to round 3');
// round 3: draw  X O X / X O O / O X X  (X:0,2,3,7,8  O:1,4,5,6)
mv(A,0,'X',{},'r3 X0'); mv(B,1,'O',{},'r3 O1'); mv(A,2,'X',{},'r3 X2'); mv(B,4,'O',{},'r3 O4'); mv(A,3,'X',{},'r3 X3'); mv(B,5,'O',{},'r3 O5'); mv(A,7,'X',{},'r3 X7'); mv(B,6,'O',{},'r3 O6');
// X plays 8 -> full board, no line? X:0,2,3,7,8 -> lines? 0,3,6 no(6=O); 2,5,8 no; 6,7,8 no -> draw
u(A,'/games/G1',{'board/8':'X',phase:'finished',r3:'D',winner:'X',endedAt:TS},'wrong winner (X) rejected',false);
u(A,'/games/G1',{'board/8':'X',phase:'finished',r3:'D',winner:'D',endedAt:TS},'draw 1-1-1 -> winner D  (sx=1,so=1)');
w(A,'/claims/G1/A',true,'draw: nobody can claim',false);
w(B,'/claims/G1/B',true,'draw: nobody can claim (B)',false);
console.log('--- second match: X wins majority');
// quick second game G2 between A and B with fresh invite (flags first cleared)
w(A,'/playing/A',null,'A clears flag'); w(B,'/playing/B',null,'B clears flag');
w(A,'/invites/B/A',{fromName:'Ann',toName:'Bob',state:'pending',createdAt:TS},'A re-invites B (old accepted invite replaced)');
u(B,'/invites/B/A',{state:'accepted',gameId:'G2'},'B accepts again');
w(B,'/games/G2',{...base},'B creates G2');
function mv2(who,cell,sym,extra,name,expect=true){ return u(who,'/games/G2',{['board/'+cell]:sym,turn:sym==='X'?'O':'X',...extra},name,expect);}
function round(n,winner){ // winner X: X0,X1,X2 ; O3,O4
  mv2(A,0,'X',{},'G2 r'+n+' X0'); mv2(B,3,'O',{},'G2 O3'); mv2(A,1,'X',{},'G2 X1'); mv2(B,4,'O',{},'G2 O4');
}
round(1);
let r=u(A,'/games/G2',{'board/2':'X',phase:'roundEnd',['r1']:'X',sx:1,endedAt:TS},'G2 X wins r1'); tick(3000);
u(A,'/games/G2',{phase:'playing',round:2,turn:'X',board:null},'G2 adv r2'); round(2);
u(A,'/games/G2',{'board/2':'X',phase:'roundEnd',r2:'X',sx:2,endedAt:TS},'G2 X wins r2'); tick(3000);
u(A,'/games/G2',{phase:'playing',round:3,turn:'X',board:null},'G2 adv r3'); round(3);
u(A,'/games/G2',{'board/2':'X',phase:'finished',r3:'X',sx:3,winner:'X',endedAt:TS},'G2 X wins match 3-0');
w(B,'/claims/G2/B',true,'loser B cannot claim',false);
w(C,'/claims/G2/C',true,'outsider cannot claim',false);
w(B,'/claims/G2/A',true,'B cannot claim for A',false);
w(A,'/claims/G2/A',true,'winner A claims');
w(A,'/claims/G2/A',true,'winner cannot claim twice',false);
w(A,'/claims/G2/A',null,'winner cannot delete claim and re-claim',false);
console.log('--- cancel');
w(A,'/playing/A',null,'x'); w(B,'/playing/B',null,'x');
w(A,'/invites/B/A',null,'sender deletes invite');
w(A,'/invites/B/A',{fromName:'Ann',toName:'Bob',state:'pending',createdAt:TS},'new invite');
u(B,'/invites/B/A',{state:'rejected'},'B rejects');
tick(1000);
w(A,'/invites/B/A',{fromName:'Ann',toName:'Bob',state:'pending',createdAt:TS},'A can re-invite after reject');
tick(40000);
u(B,'/invites/B/A',{state:'accepted',gameId:'G9'},'expired invite cannot be accepted',false);
w(A,'/invites/B/A',{fromName:'Ann',toName:'Bob',state:'pending',createdAt:TS},'A can re-invite after expiry');
u(B,'/invites/B/A',{state:'accepted',gameId:'G3'},'accept');
w(B,'/games/G3',{...base},'create G3');
u(A,'/games/G3',{phase:'cancelled',cancelledBy:'A'},'A leaves -> cancelled');
u(A,'/games/G3',{'board/0':'X',turn:'O'},'no move after cancel',false);
w(A,'/claims/G3/A',true,'no claim on cancelled',false);
console.log('--- chat');
const M=(text,extra={})=>({uid:'A',text,createdAt:TS,...extra});
w(A,'/chats/G2/m1',M('hi \u{1F338}'),'chat: player A sends a message');
w(B,'/chats/G2/m2',M('hello!',{uid:'B'}),'chat: player B sends a message');
w(B,'/chats/G2/m3',M('\u{1F338}\u{1F431}\u{2728}',{uid:'B'}),'chat: emoji-only message allowed');
w(A,'/chats/G2/m4',M('x'.repeat(60)),'chat: exactly 60 characters allowed');
w(A,'/chats/G2/m5',M('x'.repeat(61)),'chat: 61 characters rejected',false);
w(A,'/chats/G2/m6',M(''),'chat: empty text rejected',false);
w(A,'/chats/G2/m7',M('    '),'chat: spaces-only text rejected',false);
w(A,'/chats/G2/m8',M(123),'chat: text must be a string',false);
w(A,'/chats/G2/m9',M('hi',{uid:'B'}),'chat: wrong sender uid (A writes as B)',false);
w(A,'/chats/G2/m10',M('hi',{createdAt:5}),'chat: fake createdAt rejected',false);
w(A,'/chats/G2/m11',M('hi',{extra:1}),'chat: extra field rejected',false);
w(A,'/chats/G2/m12',M('hi',{name:'Ann'}),'chat: name field rejected',false);
w(A,'/chats/G2/m13',{uid:'A',text:'hi'},'chat: missing createdAt rejected',false);
w(A,'/chats/G2/m1/text','changed','chat: cannot edit a message',false);
w(A,'/chats/G2/m1',M('changed'),'chat: cannot overwrite a message',false);
w(B,'/chats/G2/m1',M('changed',{uid:'B'}),'chat: other player cannot overwrite a message',false);
w(C,'/chats/G2/m20',M('hi',{uid:'C'}),'chat: outsider cannot write',false);
w(C,'/chats/G2',null,'chat: outsider cannot delete the chat',false);
w(A,'/chats/NOGAME/m1',M('hi'),'chat: no chat for a game that does not exist',false);
w(A,'/chats/G3/m1',M('hi'),'chat: no new message in a cancelled game',false);
w(A,'/chats/G2',{m50:M('a'),m51:M('b')},'chat: cannot write several messages in one go',false);
show('chat: player A can read',db.as(A).read('/chats/G2'),true);
show('chat: player B can read',db.as(B).read('/chats/G2'),true);
show('chat: outsider C cannot read',db.as(C).read('/chats/G2'),false);
show('chat: outsider cannot read one message',db.as(C).read('/chats/G2/m1'),false);
show('chat: outsider cannot read all chats',db.as(C).read('/chats'),false);
show('chat: even a player cannot read all chats',db.as(A).read('/chats'),false);
const anon=t.database(rules,{games:{G9:{x:'A',o:'B',phase:'playing'}},chats:{G9:{m:{uid:'A',text:'hi',createdAt:1}}}},NOW);
show('chat: logged-out user cannot read',anon.as(null).read('/chats/G9'),false);
show('chat: logged-out user cannot write',anon.as(null).write('/chats/G9/m2',M('hi')),false);
w(C,'/chats/G2',null,'chat: outsider still cannot delete',false);
w(A,'/chats/G2',null,'chat: player A deletes the chat branch');
show('chat: branch is really gone after delete',{allowed:!JSON.stringify(db.root).includes('"chats"')},true);
w(B,'/chats/G2',null,'chat: deleting an already empty chat is fine');
console.log(`\n${pass} passed, ${fail} failed`);
