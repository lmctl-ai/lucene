package io.lmctl.lmcode;

enum Op {
  PUSH_INT(1), PUSH_STR(2), PUSH_BOOL(3), LOAD_LOCAL(4), STORE_LOCAL(5), LOAD_CTX(6),
  CALL_FN(7), CALL_METHOD(8), EQ(9), NE(10), NOT(11), JUMP(12), JUMP_IF_FALSE(13),
  JUMP_IF_TRUE(14), DUP(15), POP(16), HALT(17), PUSH_FLOAT(18), ADD_I(19), SUB_I(20),
  MUL_I(21), DIV_I(22), ADD_F(23), SUB_F(24), MUL_F(25), DIV_F(26), NEG_I(27), NEG_F(28),
  LT_I(29), LE_I(30), GT_I(31), GE_I(32), LT_F(33), LE_F(34), GT_F(35), GE_F(36),
  I2F(37), CONCAT(38), I2S(39), F2S(40), B2S(41), FOR_RANGE(47), FOR_NEXT(49), MATH(50),
  MOD_I(51), WHILE_BEGIN(52), WHILE_TEST(53), LOOP_BACK(54);
  final int code;
  Op(int code) { this.code = code; }
  static Op of(int b) { for (Op o : values()) if (o.code == b) return o; return null; }
}
