/*-
 * Written by J.T. Conklin <jtc@netbsd.org>
 * Public domain.
 *
 *	$NetBSD: search.h,v 1.12 1999/02/22 10:34:28 christos Exp $
 * $FreeBSD: release/9.0.0/include/search.h 105250 2002-10-16 14:29:23Z robert $
 */

#ifndef _SEARCH_H_
#define _SEARCH_H_

#include <sys/cdefs.h>
#include <sys/types.h>

typedef enum {
  preorder,
  postorder,
  endorder,
  leaf
} VISIT;

#ifdef _SEARCH_PRIVATE
typedef struct node {
  char* key;
  struct node* llink;
  struct node* rlink;
} node_t;
#endif

__BEGIN_DECLS

void insque(void*, void*);
void remque(void*);

void* lfind(const void*, const void*, size_t*, size_t, int (*)(const void*, const void*));
void* lsearch(const void*, void*, size_t*, size_t, int (*)(const void*, const void*));

void* tdelete(const void* __restrict, void** __restrict, int (*)(const void*, const void*));
void tdestroy(void*, void (*)(void*));
void* tfind(const void*, void* const*, int (*)(const void*, const void*));
void* tsearch(const void*, void**, int (*)(const void*, const void*));
void twalk(const void*, void (*)(const void*, VISIT, int));

__END_DECLS

#endif /* !_SEARCH_H_ */
