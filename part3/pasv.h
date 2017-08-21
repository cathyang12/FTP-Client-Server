#ifndef _PASVH__

#define _PASVH__

#include "common.h"

void sigchld_handler(int s);

void *get_in_addr(struct sockaddr *sa);

char *getIPaddress();

char *concat(char *s1, char *s2);

int createListenSocket(char *port);

int pasv(programState *curState);

#endif
