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
# define MEMSET		android_memset32
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
	shr    $2, %rdx			// Count in rdx
	movl   %esi, %ecx		// Pattern in ecx

	cmp    $16, %rdx
	jae    L(16dbwordsormore)

L(write_less16dbwords):
	lea    (%rdi, %rdx, 4), %rdi
	BRANCH_TO_JMPTBL_ENTRY (L(table_less16dbwords), %rdx, 4)

	.pushsection .rodata.sse2,"a",@progbits
	ALIGN (2)
L(table_less16dbwords):
	.int	JMPTBL (L(write_0dbwords), L(table_less16dbwords))
	.int	JMPTBL (L(write_1dbwords), L(table_less16dbwords))
	.int	JMPTBL (L(write_2dbwords), L(table_less16dbwords))
	.int	JMPTBL (L(write_3dbwords), L(table_less16dbwords))
	.int	JMPTBL (L(write_4dbwords), L(table_less16dbwords))
	.int	JMPTBL (L(write_5dbwords), L(table_less16dbwords))
	.int	JMPTBL (L(write_6dbwords), L(table_less16dbwords))
	.int	JMPTBL (L(write_7dbwords), L(table_less16dbwords))
	.int	JMPTBL (L(write_8dbwords), L(table_less16dbwords))
	.int	JMPTBL (L(write_9dbwords), L(table_less16dbwords))
	.int	JMPTBL (L(write_10dbwords), L(table_less16dbwords))
	.int	JMPTBL (L(write_11dbwords), L(table_less16dbwords))
	.int	JMPTBL (L(write_12dbwords), L(table_less16dbwords))
	.int	JMPTBL (L(write_13dbwords), L(table_less16dbwords))
	.int	JMPTBL (L(write_14dbwords), L(table_less16dbwords))
	.int	JMPTBL (L(write_15dbwords), L(table_less16dbwords))
	.popsection

	ALIGN (4)
L(write_15dbwords):
	movl   %ecx, -60(%rdi)
L(write_14dbwords):
	movl   %ecx, -56(%rdi)
L(write_13dbwords):
	movl   %ecx, -52(%rdi)
L(write_12dbwords):
	movl   %ecx, -48(%rdi)
L(write_11dbwords):
	movl   %ecx, -44(%rdi)
L(write_10dbwords):
	movl   %ecx, -40(%rdi)
L(write_9dbwords):
	movl   %ecx, -36(%rdi)
L(write_8dbwords):
	movl   %ecx, -32(%rdi)
L(write_7dbwords):
	movl   %ecx, -28(%rdi)
L(write_6dbwords):
	movl   %ecx, -24(%rdi)
L(write_5dbwords):
	movl   %ecx, -20(%rdi)
L(write_4dbwords):
	movl   %ecx, -16(%rdi)
L(write_3dbwords):
	movl   %ecx, -12(%rdi)
L(write_2dbwords):
	movl   %ecx, -8(%rdi)
L(write_1dbwords):
	movl   %ecx, -4(%rdi)
L(write_0dbwords):
	ret

	ALIGN (4)
L(16dbwordsormore):
	test   $3, %edi
	jz     L(aligned4bytes)
	mov    %ecx, (%rdi)
	mov    %ecx, -4(%rdi, %rdx, 4)
	sub    $1, %rdx
	rol    $24, %ecx
	add    $1, %rdi
	test   $3, %edi
	jz     L(aligned4bytes)
	ror    $8, %ecx
	add    $1, %rdi
	test   $3, %edi
	jz     L(aligned4bytes)
	ror    $8, %ecx
	add    $1, %rdi
L(aligned4bytes):
	shl    $2, %rdx

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
	shr    $2, %rdx
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
	shr    $2, %rdx
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
	shr    $2, %rdx
	BRANCH_TO_JMPTBL_ENTRY (L(table_16_128bytes), %rdx, 4)

	.pushsection .rodata.sse2,"a",@progbits
	ALIGN (2)
L(table_16_128bytes):
	.int	JMPTBL (L(aligned_16_0bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_4bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_8bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_12bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_16bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_20bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_24bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_28bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_32bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_36bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_40bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_44bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_48bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_52bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_56bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_60bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_64bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_68bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_72bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_76bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_80bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_84bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_88bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_92bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_96bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_100bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_104bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_108bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_112bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_116bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_120bytes), L(table_16_128bytes))
	.int	JMPTBL (L(aligned_16_124bytes), L(table_16_128bytes))
	.popsection

	ALIGN (4)
L(aligned_16_112bytes):
	movdqa	%xmm0, -112(%rdi)
L(aligned_16_96bytes):
	movdqa	%xmm0, -96(%rdi)
L(aligned_16_80bytes):
	movdqa	%xmm0, -80(%rdi)
L(aligned_16_64bytes):
	movdqa	%xmm0, -64(%rdi)
L(aligned_16_48bytes):
	movdqa	%xmm0, -48(%rdi)
L(aligned_16_32bytes):
	movdqa	%xmm0, -32(%rdi)
L(aligned_16_16bytes):
	movdqa	%xmm0, -16(%rdi)
L(aligned_16_0bytes):
	ret

	ALIGN (4)
L(aligned_16_116bytes):
	movdqa	%xmm0, -116(%rdi)
L(aligned_16_100bytes):
	movdqa	%xmm0, -100(%rdi)
L(aligned_16_84bytes):
	movdqa	%xmm0, -84(%rdi)
L(aligned_16_68bytes):
	movdqa	%xmm0, -68(%rdi)
L(aligned_16_52bytes):
	movdqa	%xmm0, -52(%rdi)
L(aligned_16_36bytes):
	movdqa	%xmm0, -36(%rdi)
L(aligned_16_20bytes):
	movdqa	%xmm0, -20(%rdi)
L(aligned_16_4bytes):
	movl	%ecx, -4(%rdi)
	ret

	ALIGN (4)
L(aligned_16_120bytes):
	movdqa	%xmm0, -120(%rdi)
L(aligned_16_104bytes):
	movdqa	%xmm0, -104(%rdi)
L(aligned_16_88bytes):
	movdqa	%xmm0, -88(%rdi)
L(aligned_16_72bytes):
	movdqa	%xmm0, -72(%rdi)
L(aligned_16_56bytes):
	movdqa	%xmm0, -56(%rdi)
L(aligned_16_40bytes):
	movdqa	%xmm0, -40(%rdi)
L(aligned_16_24bytes):
	movdqa	%xmm0, -24(%rdi)
L(aligned_16_8bytes):
	movq	%xmm0, -8(%rdi)
	ret

	ALIGN (4)
L(aligned_16_124bytes):
	movdqa	%xmm0, -124(%rdi)
L(aligned_16_108bytes):
	movdqa	%xmm0, -108(%rdi)
L(aligned_16_92bytes):
	movdqa	%xmm0, -92(%rdi)
L(aligned_16_76bytes):
	movdqa	%xmm0, -76(%rdi)
L(aligned_16_60bytes):
	movdqa	%xmm0, -60(%rdi)
L(aligned_16_44bytes):
	movdqa	%xmm0, -44(%rdi)
L(aligned_16_28bytes):
	movdqa	%xmm0, -28(%rdi)
L(aligned_16_12bytes):
	movq	%xmm0, -12(%rdi)
	movl	%ecx, -4(%rdi)
	ret

END (MEMSET)
