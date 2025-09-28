````mermaid
flowchart TD
    subgraph DEV["开发者环境"]
        Dev[👨‍💻 开发者]
        Dev -->|Push Code| Git[(GitHub Repo)]
    end

    subgraph CI_CD["本地 CI/CD 流程"]
        Git -->|触发流水线| CI[⚙️ Local CI/CD Runner]

        CI -->|Terraform Init & Apply| Terraform[Terraform 管理基础设施]
        Terraform -->|创建本地 AWS 资源| LocalStack[🟧 LocalStack 模拟 AWS: ECR/S3/IAM/VPC/EKS]

        CI -->|Build Java App| JavaBuild[📦 Maven/Gradle Build]
        JavaBuild -->|Docker Build| DockerImg[🐳 Docker Image]

        DockerImg -->|Push| LocalECR[🟧 LocalStack ECR]
        Helm[Helm 部署应用] --> k3d[☸️ k3d Kubernetes Cluster]
        LocalECR -->|Image Pull| k3d

        CI -->|Helm Upgrade/Install| Helm
        k3d --> App[🚀 Java Application Pods]
    end

    subgraph DEV_ACCESS["开发者访问"]
        Dev -->|kubectl port-forward / ingress| App
    end

    %% 数据流
    CI -->|日志 & 状态| App
    Terraform -->|K8s Namespace/Role/IRSA Stub| k3d
