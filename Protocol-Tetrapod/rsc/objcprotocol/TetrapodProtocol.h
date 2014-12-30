
extern int const Tetrapod_MAX_LOGIN_ATTEMPTS;
extern int const Tetrapod_RIGHTS_CLUSTER_READ;
extern int const Tetrapod_RIGHTS_CLUSTER_WRITE;
extern int const Tetrapod_RIGHTS_USER_READ;
extern int const Tetrapod_RIGHTS_USER_WRITE;
extern int const Tetrapod_BUILD;
extern int const Tetrapod_DEPLOY;
extern int const Tetrapod_LAUNCH;
extern int const Tetrapod_FULL_CYCLE;
extern int const Tetrapod_LAUNCH_PAUSED;
extern int const Tetrapod_DEPLOY_LATEST;
extern int const Tetrapod_LAUNCH_DEPLOYED;
extern int const Tetrapod_ERROR_HOSTNAME_MISMATCH;
extern int const Tetrapod_ERROR_INVALID_ACCOUNT;
extern int const Tetrapod_ERROR_INVALID_CREDENTIALS;
extern int const Tetrapod_ERROR_NOT_PARENT;
extern int const Tetrapod_ERROR_NOT_READY;

@interface TetrapodProtocol : NSObject

+ (void)registerStructs:(TPProtocol *)protocol;

@end
