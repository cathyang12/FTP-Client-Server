//for passive mode
#include <ifaddrs.h>
#include <time.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <sys/types.h>
#include <netdb.h>
#include <signal.h>
#include <unistd.h>
#include <sys/wait.h>
#include <errno.h>

#include "pasv.h"

#define BACKLOG 10     // how many pending connections queue will hold
#define TIMEOUT 9

void sigchld_handler(int s){
    // waitpid() might overwrite errno, so we save and restore it:
    int saved_errno = errno;
    while(waitpid(-1, NULL, WNOHANG) > 0);
    errno = saved_errno;
}

/*
* get sockaddr, IPv4 or IPv6:
*/
void* get_in_addr(struct sockaddr *sa){
    if (sa->sa_family == AF_INET) {
        return &(((struct sockaddr_in*)sa)->sin_addr);
    }

    return &(((struct sockaddr_in6*)sa)->sin6_addr);
}

char* getIPaddress(){
    struct ifaddrs *ifap, *ifa;
    struct sockaddr_in *sa;
    char *addr;
    char *em1IP;
    getifaddrs(&ifap);
    for (ifa = ifap; ifa; ifa = ifa->ifa_next){
        if (ifa->ifa_addr->sa_family == AF_INET){
            sa = (struct sockaddr_in *)ifa->ifa_addr;
            addr = inet_ntoa(sa->sin_addr);
            if (!strcmp(ifa->ifa_name, "em1")){
                em1IP = addr;
                break;
            }
        }
    }
    freeifaddrs(ifap);
    return em1IP;
}

char* concat(char *s1, char *s2){
    char *result = malloc(strlen(s1)+strlen(s2)+1);
    if(result == NULL) perror("allocation");
    strcpy(result, s1);
    strcat(result, s2);
    return result;
}

int createListenSocket(char* port){
    //returns an fd socket that can be listened to
    int sockfd; //to be returned
    struct addrinfo hints, *servinfo, *p;
    
    struct sigaction sa;
    int yes = 1;
    int rv;

    memset(&hints, 0, sizeof hints);
    hints.ai_family = AF_UNSPEC;
    hints.ai_socktype = SOCK_STREAM;
    hints.ai_flags = AI_PASSIVE; // use my IP

    if ((rv = getaddrinfo(NULL, port, &hints, &servinfo)) != 0) {
        fprintf(stderr, "getaddrinfo: %s\n", gai_strerror(rv));
        return 1;
    }

    // loop through all the results and bind to the first we can
    for(p = servinfo; p != NULL; p = p->ai_next) {
        if ((sockfd = socket(p->ai_family, p->ai_socktype,
                p->ai_protocol)) == -1) {
            perror("server: socket");
            continue;
        }

        if (setsockopt(sockfd, SOL_SOCKET, SO_REUSEADDR, &yes,
        //if (setsockopt(sockfd, SOL_SOCKET, SO_REUSEPORT, &yes,
                sizeof(int)) == -1) {
            perror("setsockopt");
            exit(1);
        }

        if (bind(sockfd, p->ai_addr, p->ai_addrlen) == -1) {
            close(sockfd);
            perror("server: bind");
            continue;
        }
        break;
    }

    freeaddrinfo(servinfo); // all done with this structure

    if (p == NULL)  {
        fprintf(stderr, "server: failed to bind\n");
       // exit(1);
        return -1;
    }

    if (listen(sockfd, BACKLOG) == -1) {
        perror("listen");
        exit(1);
    }

    //? what is that for?
    sa.sa_handler = sigchld_handler; // reap all dead processes
    sigemptyset(&sa.sa_mask);
    sa.sa_flags = SA_RESTART;
    if (sigaction(SIGCHLD, &sa, NULL) == -1) {
        perror("sigaction");
        exit(1);
    }
    return sockfd;
}

int pasv(programState* curState)
{
    char* em1IP;
    int pas_fd;

    struct sockaddr_storage their_addr; // connector's address information
    socklen_t sin_size = sizeof their_addr;

    em1IP = getIPaddress();
    char* first = strtok(em1IP, ".");
    char* second = strtok(NULL, ".");
    char* third = strtok(NULL, ".");
    char* fourth = strtok(NULL, ".");

   
    while(1){
        //fifth: generate random number between 8 to 180
        //sixth: from 0 to 255
        srand(time(NULL));
        int fifth = rand() % (180 + 1 - 8) + 8;
        int sixth = rand() % (255 + 1);

        int port = fifth * 256 + sixth;
        char str[6];
        sprintf(str, "%d", port);
        printf("SERVER: PASV listening at port: %d\n", port);

        int pas_socket = createListenSocket(str);
        if (pas_socket==-1){
            continue;
        }

        //construct and send msg to client
        char ipAdd[32];
        snprintf(ipAdd, sizeof(ipAdd), "(%s,%s,%s,%s,%d,%d)\n", first, second, third, fourth, fifth, sixth);
        printf("SERVER: PASV listening at (%s,%s,%s,%s,%d,%d).\n", first, second, third, fourth, fifth, sixth);     
        char* msg = concat("227 Entering Passive Mode ", ipAdd);
        dprintf(curState->new_fd, msg);
       
        // set up accept timeout
        fd_set rfds;
        struct timeval tv;
        tv.tv_sec = TIMEOUT;
        tv.tv_usec = 9000000;
        int rc;

        FD_ZERO(&rfds);
        FD_SET(pas_socket, &rfds);

        rc = select(pas_socket+1, &rfds, NULL, NULL, &tv);

        if (rc > 0) {
            // OK to process accept
            pas_fd = accept(pas_socket, (struct sockaddr *)&their_addr, &sin_size);
        } else if (rc == 0) {
            // timeout
            printf("Data connection timed out.\n");
            dprintf(curState->new_fd, "Exiting passive mode.\n");
            pas_fd =-2;
        } else {}
           
        if (pas_fd == -1){
            perror("passive accept");
        } else if (pas_fd==-2) {
           
        }else {
            printf("SERVER: client connected to PASV.\n");
        }
        return pas_fd;
    }
}