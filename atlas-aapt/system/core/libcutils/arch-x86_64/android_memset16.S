/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "cache.h"

#ifndef MEMSET
# define MEMSET		android_memset16
#endif

#ifndef L
# define L(label)	.L##label
#endif

#ifndef ALIGN
# define ALIGN(n)	.p2align n
#endif

#ifndef cfi_startproc
# define cfi_startproc			.cfi_startproc
#endif

#ifndef cfi_endproc
# define cfi_endproc			.cfi_endproc
#endif

#ifndef ENTRY
# define ENTRY(name)			\
	.type name,  @function; 	\
	.globl name;			\
	.p2align 4;			\
name:					\
	cfi_startproc
#endif

#ifndef END
# define END(name)			\
	cfi_endproc;			\
	.size name, .-name
#endif

#define JMPTBL(I, B)	I - B

/* Branch to an entry in a jump table.  TABLE is a jump table with
   relative offsets.  INDEX is a register contains the index into the
   jump table.  SCALE is the scale of INDEX.  */
#define BRANCH_TO_JMPTBL_ENTRY(TABLE, INDEX, SCALE) \
	lea    TABLE(%rip), %r11;						\
	movslq (%r11, INDEX, SCALE), INDEX;				\
	lea    (%r11, INDEX), INDEX;					\
	jmp    *INDEX

	.section .text.sse2,"ax",@progbits
	ALIGN (4)
ENTRY (MEMSET)	// Address in rdi
	shr    $1, %rdx			// Count in rdx
	movzwl %si, %ecx
	/* Fill the whole ECX with pattern.  */
	shl    $16, %esi
	or     %esi, %ecx		// Pattern in ecx

	cmp    $32, %rdx
	jae    L(32wordsormore)

L(write_less32words):
	lea    (%rdi, %rdx, 2), %rdi
	BRANCH_TO_JMPTBL_ENTRY (L(table_less32words), %rdx, 4)

	.pushsection .rodata.sse2,"a",@progbits
	ALIGN (2)
L(table_less32words):
	.int	JMPTBL (L(write_0words), L(table_less32words))
	.int	JMPTBL (L(write_1words), L(table_less32words))
	.int	JMPTBL (L(write_2words), L(table_less32words))
	.int	JMPTBL (L(write_3words), L(table_less32words))
	.int	JMPTBL (L(write_4words), L(table_less32words))
	.int	JMPTBL (L(write_5words), L(table_less32words))
	.int	JMPTBL (L(write_6words), L(table_less32words))
	.int	JMPTBL (L(write_7words), L(table_less32words))
	.int	JMPTBL (L(write_8words), L(table_less32words))
	.int	JMPTBL (L(write_9words), L(table_less32words))
	.int	JMPTBL (L(write_10words), L(table_less32words))
	.int	JMPTBL (L(write_11words), L(table_less32words))
	.int	JMPTBL (L(write_12words), L(table_less32words))
	.int	JMPTBL (L(write_13words), L(table_less32words))
	.int	JMPTBL (L(write_14words), L(table_less32words))
	.int	JMPTBL (L(write_15words), L(table_less32words))
	.int	JMPTBL (L(write_16words), L(table_less32words))
	.int	JMPTBL (L(write_17words), L(table_less32words))
	.int	JMPTBL (L(write_18words), L(table_less32words))
	.int	JMPTBL (L(write_19words), L(table_less32words))
	.int	JMPTBL (L(write_20words), L(table_less32words))
	.int	JMPTBL (L(write_21words), L(table_less32words))
	.int	JMPTBL (L(write_22words), L(table_less32words))
	.int	JMPTBL (L(write_23words), L(table_less32words))
	.int	JMPTBL (L(write_24words), L(table_less32words))
	.int	JMPTBL (L(write_25words), L(table_less32words))
	.int	JMPTBL (L(write_26words), L(table_less32words))
	.int	JMPTBL (L(write_27words), L(table_less32words))
	.int	JMPTBL (L(write_28words), L(table_less32words))
	.int	JMPTBL (L(write_29words), L(table_less32words))
	.int	JMPTBL (L(write_30words), L(table_less32words))
	.int	JMPTBL (L(write_31words), L(table_less32words))
	.popsection

	ALIGN (4)
L(write_28words):
	movl   %ecx, -56(%rdi)
	movl   %ecx, -52(%rdi)
L(write_24words):
	movl   %ecx, -48(%rdi)
	movl   %ecx, -44(%rdi)
L(write_20words):
	movl   %ecx, -40(%rdi)
	movl   %ecx, -36(%rdi)
L(write_16words):
	movl   %ecx, -32(%rdi)
	movl   %ecx, -28(%rdi)
L(write_12words):
	movl   %ecx, -24(%rdi)
	movl   %ecx, -20(%rdi)
L(write_8words):
	movl   %ecx, -16(%rdi)
	movl   %ecx, -12(%rdi)
L(write_4words):
	movl   %ecx, -8(%rdi)
	movl   %ecx, -4(%rdi)
L(write_0words):
	ret

	ALIGN (4)
L(write_29words):
	movl   %ecx, -58(%rdi)
	movl   %ecx, -54(%rdi)
L(write_25words):
	movl   %ecx, -50(%rdi)
	movl   %ecx, -46(%rdi)
L(write_21words):
	movl   %ecx, -42(%rdi)
	movl   %ecx, -38(%rdi)
L(write_17words):
	movl   %ecx, -34(%rdi)
	movl   %ecx, -30(%rdi)
L(write_13words):
	movl   %ecx, -26(%rdi)
	movl   %ecx, -22(%rdi)
L(write_9words):
	movl   %ecx, -18(%rdi)
	movl   %ecx, -14(%rdi)
L(write_5words):
	movl   %ecx, -10(%rdi)
	movl   %ecx, -6(%rdi)
L(write_1words):
	mov	%cx, -2(%rdi)
	ret

	ALIGN (4)
L(write_30words):
	movl   %ecx, -60(%rdi)
	movl   %ecx, -56(%rdi)
L(write_26words):
	movl   %ecx, -52(%rdi)
	movl   %ecx, -48(%rdi)
L(write_22words):
	movl   %ecx, -44(%rdi)
	movl   %ecx, -40(%rdi)
L(write_18words):
	movl   %ecx, -36(%rdi)
	movl   %ecx, -32(%rdi)
L(write_14words):
	movl   %ecx, -28(%rdi)
	movl   %ecx, -24(%rdi)
L(write_10words):
	movl   %ecx, -20(%rdi)
	movl   %ecx, -16(%rdi)
L(write_6words):
	movl   %ecx, -12(%rdi)
	movl   %ecx, -8(%rdi)
L(write_2words):
	movl   %ecx, -4(%rdi)
	ret

	ALIGN (4)
L(write_31words):
	movl   %ecx, -62(%rdi)
	movl   %ecx, -58(%rdi)
L(write_27words):
	movl   %ecx, -54(%rdi)
	movl   %ecx, -50(%rdi)
L(write_23words):
	movl   %ecx, -46(%rdi)
	movl   %ecx, -42(%rdi)
L(write_19words):
	movl   %ecx, -38(%rdi)
	movl   %ecx, -34(%rdi)
L(write_15words):
	movl   %ecx, -30(%rdi)
	movl   %ecx, -26(%rdi)
L(write_11words):
	movl   %ecx, -22(%rdi)
	movl   %ecx, -18(%rdi)
L(write_7words):
	movl   %ecx, -14(%rdi)
	movl   %ecx, -10(%rdi)
L(write_3words):
	movl   %ecx, -6(%rdi)
	movw   %cx, -2(%rdi)
	ret

	ALIGN (4)
L(32wordsormore):
	shl    $1, %rdx
	test   $0x01, %edi
	jz     L(aligned2bytes)
	mov    %ecx, (%rdi)
	mov    %ecx, -4(%rdi, %rdx)
	sub    $2, %rdx
	add    $1, %rdi
	rol    $8, %ecx
L(aligned2bytes):
	/* Fill xmm0 with the pattern.  */
	movd   %ecx, %xmm0
	pshufd $0, %xmm0, %xmm0

	testl  $0xf, %edi
	jz     L(aligned_16)
/* RDX > 32 and RDI is not 16 byte aligned.  */
	movdqu %xmm0, (%rdi)
	mov    %rdi, %rsi
	and    $-16, %rdi
	add    $16, %rdi
	sub    %rdi, %rsi
	add    %rsi, %rdx

	ALIGN (4)
L(aligned_16):
	cmp    $128, %rdx
	jge    L(128bytesormore)

L(aligned_16_less128bytes):
	add    %rdx, %rdi
	shr    $1, %rdx
	BRANCH_TO_JMPTBL_ENTRY (L(table_16_128bytes), %rdx, 4)

	ALIGN (4)
L(128bytesormore):
	cmp    $SHARED_CACHE_SIZE, %rdx
	jg     L(128bytesormore_nt)

L(128bytesormore_normal):
	sub    $128, %rdx
	movdqa %xmm0, (%rdi)
	movdqa %xmm0, 0x10(%rdi)
	movdqa %xmm0, 0x20(%rdi)
	movdqa %xmm0, 0x30(%rdi)
	movdqa %xmm0, 0x40(%rdi)
	movdqa %xmm0, 0x50(%rdi)
	movdqa %xmm0, 0x60(%rdi)
	movdqa %xmm0, 0x70(%rdi)
	lea    128(%rdi), %rdi
	cmp    $128, %rdx
	jl     L(128bytesless_normal)

	sub    $128, %rdx
	movdqa %xmm0, (%rdi)
	movdqa %xmm0, 0x10(%rdi)
	movdqa %xmm0, 0x20(%rdi)
	movdqa %xmm0, 0x30(%rdi)
	movdqa %xmm0, 0x40(%rdi)
	movdqa %xmm0, 0x50(%rdi)
	movdqa %xmm0, 0x60(%rdi)
	movdqa %xmm0, 0x70(%rdi)
	lea    128(%rdi), %rdi
	cmp    $128, %rdx
	jl     L(128bytesless_normal)

	sub    $128, %rdx
	movdqa %xmm0, (%rdi)
	movdqa %xmm0, 0x10(%rdi)
	movdqa %xmm0, 0x20(%rdi)
	movdqa %xmm0, 0x30(%rdi)
	movdqa %xmm0, 0x40(%rdi)
	movdqa %xmm0, 0x50(%rdi)
	movdqa %xmm0, 0x60(%rdi)
	movdqa %xmm0, 0x70(%rdi)
	lea    128(%rdi), %rdi
	cmp    $128, %rdx
	jl     L(128bytesless_normal)

	sub    $128, %rdx
	movdqa %xmm0, (%rdi)
	movdqa %xmm0, 0x10(%rdi)
	movdqa %xmm0, 0x20(%rdi)
	movdqa %xmm0, 0x30(%rdi)
	movdqa %xmm0, 0x40(%rdi)
	movdqa %xmm0, 0x50(%rdi)
	movdqa %xmm0, 0x60(%rdi)
	movdqa %xmm0, 0x70(%rdi)
	lea    128(%rdi), %rdi
	cmp    $128, %rdx
	jge    L(128bytesormore_normal)

L(128bytesless_normal):
	add    %rdx, %rdi
	shr    $1, %rdx
	BRANCH_TO_JMPTBL_ENTRY (L(table_16_128bytes), %rdx, 4)

	ALIGN (4)
L(128bytesormore_nt):
	sub    $128, %rdx
	movntdq %xmm0, (%rdi)
	movntdq %xmm0, 0x10(%rdi)
	movntdq %xmm0, 0x20(%rdi)
	movntdq %xmm0, 0x30(%rdi)
	movntdq %xmm0, 0x40(%rdi)
	movntdq %xmm0, 0x50(%rdi)
	movntdq %xmm0, 0x60(%rdi)
	movntdq %xmm0, 0x70(%rdi)
	lea    128(%rdi), %rdi
	cmp    $128, %rdx
	jge    L(128bytesormore_nt)

	sfence
	add    %rdx, %rdi
	shr    $1, %rdx
	BRANCH_TO_JMPTBL_ENTRY (L(table_16_128bytes), %rdx, 4)

	.pushsection .rodata.sse2,"a",@progbits
	ALIGN (2)
L(table_16_128bytes):
	.int	JMPTBL (L(aligned_16_0bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_2bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_4bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_6bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_8bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_10bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_12bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_14bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_16bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_18bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_20bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_22bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_24bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_26bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_28bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_30bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_32bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_34bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_36bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_38bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_40bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_42bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_44bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_46bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_48bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_50bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_52bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_54bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_56bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_58bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_60bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_62bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_64bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_66bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_68bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_70bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_72bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_74bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_76bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_78bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_80bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_82bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_84bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_86bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_88bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_90bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_92bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_94bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_96bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_98bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_100bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_102bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_104bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_106bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_108bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_110bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_112bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_114bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_116bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_118bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_120bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_122bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_124bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_126bytes), L(table_16_128bytes))
	.popsection

	ALIGN (4)
L(aligned_16_112bytes):
	movdqa %xmm0, -112(%rdi)
L(aligned_16_96bytes):
	movdqa %xmm0, -96(%rdi)
L(aligned_16_80bytes):
	movdqa %xmm0, -80(%rdi)
L(aligned_16_64bytes):
	movdqa %xmm0, -64(%rdi)
L(aligned_16_48bytes):
	movdqa %xmm0, -48(%rdi)
L(aligned_16_32bytes):
	movdqa %xmm0, -32(%rdi)
L(aligned_16_16bytes):
	movdqa %xmm0, -16(%rdi)
L(aligned_16_0bytes):
	ret

	ALIGN (4)
L(aligned_16_114bytes):
	movdqa %xmm0, -114(%rdi)
L(aligned_16_98bytes):
	movdqa %xmm0, -98(%rdi)
L(aligned_16_82bytes):
	movdqa %xmm0, -82(%rdi)
L(aligned_16_66bytes):
	movdqa %xmm0, -66(%rdi)
L(aligned_16_50bytes):
	movdqa %xmm0, -50(%rdi)
L(aligned_16_34bytes):
	movdqa %xmm0, -34(%rdi)
L(aligned_16_18bytes):
	movdqa %xmm0, -18(%rdi)
L(aligned_16_2bytes):
	movw   %cx, -2(%rdi)
	ret

	ALIGN (4)
L(aligned_16_116bytes):
	movdqa %xmm0, -116(%rdi)
L(aligned_16_100bytes):
	movdqa %xmm0, -100(%rdi)
L(aligned_16_84bytes):
	movdqa %xmm0, -84(%rdi)
L(aligned_16_68bytes):
	movdqa %xmm0, -68(%rdi)
L(aligned_16_52bytes):
	movdqa %xmm0, -52(%rdi)
L(aligned_16_36bytes):
	movdqa %xmm0, -36(%rdi)
L(aligned_16_20bytes):
	movdqa %xmm0, -20(%rdi)
L(aligned_16_4bytes):
	movl   %ecx, -4(%rdi)
	ret

	ALIGN (4)
L(aligned_16_118bytes):
	movdqa %xmm0, -118(%rdi)
L(aligned_16_102bytes):
	movdqa %xmm0, -102(%rdi)
L(aligned_16_86bytes):
	movdqa %xmm0, -86(%rdi)
L(aligned_16_70bytes):
	movdqa %xmm0, -70(%rdi)
L(aligned_16_54bytes):
	movdqa %xmm0, -54(%rdi)
L(aligned_16_38bytes):
	movdqa %xmm0, -38(%rdi)
L(aligned_16_22bytes):
	movdqa %xmm0, -22(%rdi)
L(aligned_16_6bytes):
	movl   %ecx, -6(%rdi)
	movw   %cx, -2(%rdi)
	ret

	ALIGN (4)
L(aligned_16_120bytes):
	movdqa %xmm0, -120(%rdi)
L(aligned_16_104bytes):
	movdqa %xmm0, -104(%rdi)
L(aligned_16_88bytes):
	movdqa %xmm0, -88(%rdi)
L(aligned_16_72bytes):
	movdqa %xmm0, -72(%rdi)
L(aligned_16_56bytes):
	movdqa %xmm0, -56(%rdi)
L(aligned_16_40bytes):
	movdqa %xmm0, -40(%rdi)
L(aligned_16_24bytes):
	movdqa %xmm0, -24(%rdi)
L(aligned_16_8bytes):
	movq   %xmm0, -8(%rdi)
	ret

	ALIGN (4)
L(aligned_16_122bytes):
	movdqa %xmm0, -122(%rdi)
L(aligned_16_106bytes):
	movdqa %xmm0, -106(%rdi)
L(aligned_16_90bytes):
	movdqa %xmm0, -90(%rdi)
L(aligned_16_74bytes):
	movdqa %xmm0, -74(%rdi)
L(aligned_16_58bytes):
	movdqa %xmm0, -58(%rdi)
L(aligned_16_42bytes):
	movdqa %xmm0, -42(%rdi)
L(aligned_16_26bytes):
	movdqa %xmm0, -26(%rdi)
L(aligned_16_10bytes):
	movq   %xmm0, -10(%rdi)
	movw   %cx, -2(%rdi)
	ret

	ALIGN (4)
L(aligned_16_124bytes):
	movdqa %xmm0, -124(%rdi)
L(aligned_16_108bytes):
	movdqa %xmm0, -108(%rdi)
L(aligned_16_92bytes):
	movdqa %xmm0, -92(%rdi)
L(aligned_16_76bytes):
	movdqa %xmm0, -76(%rdi)
L(aligned_16_60bytes):
	movdqa %xmm0, -60(%rdi)
L(aligned_16_44bytes):
	movdqa %xmm0, -44(%rdi)
L(aligned_16_28bytes):
	movdqa %xmm0, -28(%rdi)
L(aligned_16_12bytes):
	movq   %xmm0, -12(%rdi)
	movl   %ecx, -4(%rdi)
	ret

	ALIGN (4)
L(aligned_16_126bytes):
	movdqa %xmm0, -126(%rdi)
L(aligned_16_110bytes):
	movdqa %xmm0, -110(%rdi)
L(aligned_16_94bytes):
	movdqa %xmm0, -94(%rdi)
L(aligned_16_78bytes):
	movdqa %xmm0, -78(%rdi)
L(aligned_16_62bytes):
	movdqa %xmm0, -62(%rdi)
L(aligned_16_46bytes):
	movdqa %xmm0, -46(%rdi)
L(aligned_16_30bytes):
	movdqa %xmm0, -30(%rdi)
L(aligned_16_14bytes):
	movq   %xmm0, -14(%rdi)
	movl   %ecx, -6(%rdi)
	movw   %cx, -2(%rdi)
	ret

END (MEMSET)
